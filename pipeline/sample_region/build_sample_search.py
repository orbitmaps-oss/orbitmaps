#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds the offline search index (SQLite FTS5) for the Panaji (Goa) sample region.

Runs after build_sample_routing.py in the "Sample region data" workflow, which leaves the cut OSM
extract at pipeline/out/sample-region/panaji.osm.pbf. Needs osmium-tool on PATH and a Python whose
sqlite3 module has FTS5 (CPython on Linux, macOS and Windows does).

Steps:
  1. osmium keeps only objects with a name, and exports them as GeoJSON lines.
  2. Each place gets every name OSM has (name, name:<lang>, alt_name, ...), a category (the main tag,
     e.g. "amenity=restaurant"), one representative point and an importance rank. Named streets are
     merged per name and roughly per kilometre, so a long road appears once per area, not per segment.
  3. Everything goes into one SQLite file: a `places` table for display and a contentless FTS5 table
     `places_fts` for matching, plus a `meta` table with the schema version, licence and attribution.

Output (gitignored, bundled in debug builds):
  pipeline/out/sample-region/assets/regions/panaji-search.sqlite

The schema is read by the app (app/.../search/OfflineSearch.kt). Change SCHEMA_VERSION with it.

Usage: python pipeline/sample_region/build_sample_search.py
"""

from __future__ import annotations

import json
import re
import shutil
import sqlite3
import subprocess
import sys
from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from build_sample_routing import EXTRACT, SAMPLE_DIR
from fetch_sample_region import sha256_of

# 2: places.detail ("Maharashtra, India"), shared with build_world_places.py.
SCHEMA_VERSION = 2
INDEX = SAMPLE_DIR / "assets" / "regions" / "panaji-search.sqlite"
WORK_DIR = SAMPLE_DIR / "search-work"

LICENCE = "ODbL-1.0"
ATTRIBUTION = "© OpenStreetMap contributors"

# Name tags worth searching, besides every name:<language>. Values may hold several names split by ";".
NAME_KEYS = ("name", "alt_name", "old_name", "short_name", "official_name", "loc_name", "int_name", "brand")

# The tag that decides a place's category, in priority order.
CATEGORY_KEYS = (
    "place", "amenity", "shop", "tourism", "leisure", "healthcare", "office", "craft", "historic",
    "natural", "railway", "aeroway", "public_transport", "highway",
)
RAILWAY_VALUES = {"station", "halt"}
AEROWAY_VALUES = {"aerodrome", "terminal"}
PUBLIC_TRANSPORT_VALUES = {"station"}
NATURAL_VALUES = {"peak", "beach", "bay", "cape", "island", "water", "spring", "waterfall", "hill"}
STREET_VALUES = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
    "living_street", "service", "pedestrian", "track", "road", "footway", "path", "cycleway",
}

PLACE_IMPORTANCE = {
    "city": 100, "town": 90, "island": 70, "suburb": 70, "village": 70, "quarter": 55,
    "neighbourhood": 50, "hamlet": 50, "locality": 45, "isolated_dwelling": 30,
}
MAJOR_ROADS = {"motorway", "trunk", "primary"}

# Streets with the same name are merged within cells of about 1 km (0.01 degrees).
STREET_CELL_DEGREES = 0.01


@dataclass(frozen=True)
class Place:
    osm: str  # "n123", "w45" or "r6"
    name: str
    name_en: str | None
    names: tuple[str, ...]
    category: str
    lat: float
    lon: float
    importance: int
    # Where the place is, shown under its name ("Maharashtra, India"); None for region places.
    detail: str | None = None


# Devanagari: a nasal consonant + virama before another consonant (म्ब in मुम्बई) is written the same
# as the anusvara (मुंबई); both spellings are common. Names are indexed in the anusvara form too, and
# the app folds queries the same way (SearchText.foldSpelling), so either spelling finds the place.
DEVANAGARI_NASAL_CLUSTER = re.compile("[\u0919\u091E\u0923\u0928\u092E]\u094D(?=[\u0915-\u0939])")
ANUSVARA = "\u0902"


def fold_spelling(text: str) -> str:
    return DEVANAGARI_NASAL_CLUSTER.sub(ANUSVARA, text)


def search_text(names: tuple[str, ...]) -> str:
    """What goes into the FTS index: every name, plus its folded spelling when that differs."""
    folded = [fold_spelling(n) for n in names]
    return " ".join(list(names) + [f for f, n in zip(folded, names) if f != n])


def names_of(tags: dict[str, str]) -> tuple[str, ...]:
    """Every distinct name, in a stable order: name first, then the other keys, then languages."""
    keys = [k for k in NAME_KEYS if k in tags] + sorted(k for k in tags if k.startswith("name:"))
    seen: set[str] = set()
    result: list[str] = []
    for key in keys:
        for value in tags[key].split(";"):
            value = value.strip()
            if value and value.casefold() not in seen:
                seen.add(value.casefold())
                result.append(value)
    return tuple(result)


def category_of(tags: dict[str, str]) -> str | None:
    for key in CATEGORY_KEYS:
        value = tags.get(key)
        if not value:
            continue
        if key == "railway" and value not in RAILWAY_VALUES:
            continue
        if key == "aeroway" and value not in AEROWAY_VALUES:
            continue
        if key == "public_transport" and value not in PUBLIC_TRANSPORT_VALUES:
            continue
        if key == "natural" and value not in NATURAL_VALUES:
            continue
        if key == "highway" and value not in STREET_VALUES:
            continue
        return f"{key}={value}"
    return None


def importance_of(category: str) -> int:
    key, _, value = category.partition("=")
    if key == "place":
        return PLACE_IMPORTANCE.get(value, 40)
    if key in ("railway", "aeroway", "public_transport"):
        return 60
    if category in ("amenity=hospital", "amenity=bus_station", "amenity=university"):
        return 50
    if key in ("tourism", "historic", "natural"):
        return 45
    if key == "highway":
        return 30 if value in MAJOR_ROADS else 20
    return 30


def representative_point(geometry: dict) -> tuple[float, float] | None:
    """(lat, lon): the point itself, a line's middle vertex, or the mean of a polygon's outer ring."""
    kind = geometry.get("type")
    coords = geometry.get("coordinates")
    if not coords:
        return None
    if kind == "Point":
        lon, lat = coords[:2]
    elif kind == "LineString":
        lon, lat = coords[len(coords) // 2][:2]
    elif kind == "MultiLineString":
        line = max(coords, key=len)
        lon, lat = line[len(line) // 2][:2]
    elif kind in ("Polygon", "MultiPolygon"):
        ring = coords[0] if kind == "Polygon" else coords[0][0]
        vertices = ring[:-1] if len(ring) > 1 and ring[0] == ring[-1] else ring
        lon = sum(v[0] for v in vertices) / len(vertices)
        lat = sum(v[1] for v in vertices) / len(vertices)
    else:
        return None
    return round(lat, 6), round(lon, 6)


def place_from_feature(feature: dict) -> Place | None:
    tags = {k: str(v) for k, v in (feature.get("properties") or {}).items()}
    names = names_of(tags)
    category = category_of(tags)
    point = representative_point(feature.get("geometry") or {})
    if not names or category is None or point is None:
        return None
    name = tags.get("name") or tags.get("name:en") or names[0]
    name_en = tags.get("name:en")
    return Place(
        osm=str(feature.get("id", "")),
        name=name,
        name_en=name_en if name_en and name_en != name else None,
        names=names,
        category=category,
        lat=point[0],
        lon=point[1],
        importance=importance_of(category),
    )


def merge_streets(places: Iterable[Place]) -> list[Place]:
    """Keeps one street per (name, ~1 km cell), so road segments don't flood the results."""
    result: list[Place] = []
    seen: set[tuple[str, int, int]] = set()
    for place in places:
        if place.category.startswith("highway="):
            cell = (
                place.name.casefold(),
                int(place.lat // STREET_CELL_DEGREES),
                int(place.lon // STREET_CELL_DEGREES),
            )
            if cell in seen:
                continue
            seen.add(cell)
        result.append(place)
    return result


def read_features(path: Path) -> Iterator[dict]:
    """Reads GeoJSON text sequences (RFC 8142): one feature per line, maybe after an RS character."""
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            line = line.lstrip("\x1e").strip()
            if line:
                yield json.loads(line)


def require_fts5() -> None:
    try:
        sqlite3.connect(":memory:").execute("CREATE VIRTUAL TABLE t USING fts5(x)")
    except sqlite3.OperationalError as error:
        raise SystemExit(f"this Python's SQLite {sqlite3.sqlite_version} has no FTS5: {error}") from error


def build_index(places: list[Place], dest: Path, meta: dict[str, str]) -> None:
    """Writes the index to dest via a .part file. The schema matches OfflineSearch.kt."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    partial = dest.with_name(dest.name + ".part")
    partial.unlink(missing_ok=True)
    db = sqlite3.connect(partial)
    try:
        db.executescript(
            """
            PRAGMA page_size = 4096;
            CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE places(
                id INTEGER PRIMARY KEY,
                osm TEXT NOT NULL,
                name TEXT NOT NULL,
                name_en TEXT,
                category TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                importance INTEGER NOT NULL,
                detail TEXT
            );
            -- categories adds marks (M*) to word characters: by default unicode61 splits Indic
            -- words at their vowel signs, so a Devanagari prefix would match unrelated places.
            CREATE VIRTUAL TABLE places_fts USING fts5(
                names, content='', prefix='2 3',
                tokenize="unicode61 remove_diacritics 2 categories 'L* N* Co M*'"
            );
            """
        )
        with db:
            for rowid, place in enumerate(places, start=1):
                db.execute(
                    "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        rowid, place.osm, place.name, place.name_en, place.category,
                        place.lat, place.lon, place.importance, place.detail,
                    ),
                )
                db.execute("INSERT INTO places_fts(rowid, names) VALUES (?, ?)", (rowid, search_text(place.names)))
            db.executemany("INSERT INTO meta VALUES (?, ?)", sorted(meta.items()))
            db.execute("INSERT INTO places_fts(places_fts) VALUES ('optimize')")
        db.execute("VACUUM")
    finally:
        db.close()
    partial.replace(dest)


def index_meta(place_count: int, source_sha256: str, built_at: datetime, region: str = "panaji") -> dict[str, str]:
    return {
        "schema_version": str(SCHEMA_VERSION),
        "region": region,
        "place_count": str(place_count),
        "built_at": built_at.astimezone(timezone.utc).isoformat(timespec="seconds"),
        "source_sha256": source_sha256,
        "licence": LICENCE,
        "attribution": ATTRIBUTION,
    }


def main() -> int:
    if shutil.which("osmium") is None:
        raise SystemExit("osmium-tool is not on PATH")
    require_fts5()
    if not EXTRACT.is_file():
        raise SystemExit(f"{EXTRACT} not found; run build_sample_routing.py first")
    if WORK_DIR.exists():
        shutil.rmtree(WORK_DIR)
    WORK_DIR.mkdir(parents=True)

    named = WORK_DIR / "named.osm.pbf"
    exported = WORK_DIR / "named.geojsonseq"
    subprocess.run(["osmium", "tags-filter", str(EXTRACT), "nwr/name", "nwr/name:en", "--overwrite", "-o", str(named)], check=True)
    subprocess.run(
        ["osmium", "export", str(named), "-f", "geojsonseq", "--add-unique-id=type_id", "--overwrite", "-o", str(exported)],
        check=True,
    )

    candidates = (place_from_feature(feature) for feature in read_features(exported))
    places = merge_streets(p for p in candidates if p is not None)
    if not places:
        raise SystemExit("no places found in the extract")
    build_index(places, INDEX, index_meta(len(places), sha256_of(EXTRACT), datetime.now(timezone.utc)))
    shutil.rmtree(WORK_DIR)
    print(f"Wrote {INDEX} ({len(places)} places, {INDEX.stat().st_size} bytes, SHA-256 {sha256_of(INDEX)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
