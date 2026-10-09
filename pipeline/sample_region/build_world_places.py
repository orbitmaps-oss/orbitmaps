#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds the world places index: cities and towns everywhere, bundled with the app.

So that searching "Mumbai" or "Paris" works before any region is downloaded. The source is Natural
Earth's populated places (public domain, about 7,300 places with names in many languages), pinned
to release v5.1.2 and checked against its git blob SHA-1 from the upstream tree. Only Python's
standard library is needed, so it runs on any developer machine:

  python pipeline/sample_region/build_world_places.py

Output (gitignored; bundled in every build when present):
  pipeline/out/world/assets/places/world-places.sqlite

The file uses the same schema as the region index (build_sample_search.py), so the app reads both
with the same code.
"""

from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from build_sample_search import Place, build_index, index_meta, require_fts5
from fetch_sample_region import OUT_DIR, sha256_of

SOURCE_URL = (
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_populated_places.geojson"
)
# Git blob SHA-1 of that file in the v5.1.2 tree (GitHub contents API), and its size.
SOURCE_GIT_SHA1 = "376fb028c390a7a23213800ae4babaa6f534a0f3"
SOURCE_SIZE = 19359003

WORLD_DIR = OUT_DIR / "world"
SOURCE_FILE = WORLD_DIR / "ne_10m_populated_places.geojson"
INDEX = WORLD_DIR / "assets" / "places" / "world-places.sqlite"

LICENCE = "Public domain (Natural Earth)"
ATTRIBUTION = "Made with Natural Earth"

# Natural Earth keeps alternative names in one field, separated like this.
ALT_SEPARATORS = ("|", ";")


class ChecksumError(Exception):
    pass


def git_blob_sha1(data: bytes) -> str:
    """The SHA-1 git uses for a file: sha1("blob <size>\\0" + content)."""
    return hashlib.sha1(b"blob %d\x00" % len(data) + data, usedforsecurity=False).hexdigest()


def verify_source(data: bytes) -> None:
    if len(data) != SOURCE_SIZE or git_blob_sha1(data) != SOURCE_GIT_SHA1:
        raise ChecksumError(
            f"Natural Earth file is {len(data)} bytes with git SHA-1 {git_blob_sha1(data)}; "
            f"expected {SOURCE_SIZE} bytes, {SOURCE_GIT_SHA1}"
        )


def lower_keys(properties: dict) -> dict[str, str]:
    """Natural Earth mixes NAME and name_en styles; compare keys case-insensitively."""
    return {str(k).lower(): str(v).strip() for k, v in properties.items() if v not in (None, "")}


def names_of(props: dict[str, str]) -> tuple[str, ...]:
    """The main name, the ASCII spelling, every name_<language>, then alternatives; no duplicates."""
    candidates = [props.get("name", ""), props.get("nameascii", "")]
    candidates += [props[k] for k in sorted(props) if k.startswith("name_") and len(k) <= 9 and k != "name_alt"]
    alt = props.get("namealt", "") or props.get("name_alt", "")
    for separator in ALT_SEPARATORS:
        alt = alt.replace(separator, "\n")
    candidates += alt.split("\n")
    seen: set[str] = set()
    result: list[str] = []
    for value in candidates:
        value = value.strip()
        if value and value.casefold() not in seen:
            seen.add(value.casefold())
            result.append(value)
    return tuple(result)


def number(props: dict[str, str], key: str, default: float = 0.0) -> float:
    try:
        return float(props.get(key, default))
    except ValueError:
        return default


def importance_of(props: dict[str, str]) -> int:
    """100 for the most prominent places (scalerank 0) down to 50 (scalerank 10); capitals get +5."""
    scalerank = min(max(number(props, "scalerank", 10), 0), 10)
    capital = "capital" in props.get("featurecla", "").lower()
    return min(100, int(100 - scalerank * 5 + (5 if capital else 0)))


def category_of(props: dict[str, str]) -> str:
    return "place=city" if number(props, "pop_max") >= 100_000 or "capital" in props.get("featurecla", "").lower() else "place=town"


def detail_of(props: dict[str, str]) -> str | None:
    """'Maharashtra, India': the first-level region and the country, when known and different."""
    parts = [props.get("adm1name", ""), props.get("adm0name", "")]
    parts = [p for i, p in enumerate(parts) if p and p not in parts[:i] and p != props.get("name")]
    return ", ".join(parts) or None


def place_from_feature(feature: dict, index: int) -> Place | None:
    props = lower_keys(feature.get("properties") or {})
    names = names_of(props)
    geometry = feature.get("geometry") or {}
    if geometry.get("type") == "Point" and geometry.get("coordinates"):
        lon, lat = geometry["coordinates"][:2]
    elif "latitude" in props and "longitude" in props:
        lat, lon = number(props, "latitude"), number(props, "longitude")
    else:
        return None
    if not names or not (-90 <= lat <= 90 and -180 <= lon <= 180):
        return None
    name = props.get("name") or names[0]
    name_en = props.get("name_en")
    return Place(
        osm=f"ne:{props.get('ne_id') or index}",
        name=name,
        name_en=name_en if name_en and name_en != name else None,
        names=names,
        category=category_of(props),
        lat=round(float(lat), 6),
        lon=round(float(lon), 6),
        importance=importance_of(props),
        detail=detail_of(props),
    )


def places_from_geojson(collection: dict) -> list[Place]:
    places = (place_from_feature(f, i) for i, f in enumerate(collection.get("features", [])))
    return sorted((p for p in places if p is not None), key=lambda p: (-p.importance, p.name))


def download() -> bytes:
    if SOURCE_FILE.is_file():
        data = SOURCE_FILE.read_bytes()
        try:
            verify_source(data)
            return data
        except ChecksumError:
            SOURCE_FILE.unlink()
    print(f"Downloading {SOURCE_URL} ...")
    with urllib.request.urlopen(SOURCE_URL, timeout=120) as response:
        data = response.read()
    verify_source(data)  # before anything is written or parsed
    SOURCE_FILE.parent.mkdir(parents=True, exist_ok=True)
    SOURCE_FILE.write_bytes(data)
    return data


def main() -> int:
    require_fts5()
    try:
        data = download()
    except ChecksumError as error:
        print(f"ERROR: {error}")
        return 1
    places = places_from_geojson(json.loads(data))
    meta = index_meta(len(places), hashlib.sha256(data).hexdigest(), datetime.now(timezone.utc), region="world")
    meta.update({"licence": LICENCE, "attribution": ATTRIBUTION, "source": SOURCE_URL})
    build_index(places, INDEX, meta)
    print(f"Wrote {INDEX} ({len(places)} places, {INDEX.stat().st_size} bytes, SHA-256 {sha256_of(INDEX)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
