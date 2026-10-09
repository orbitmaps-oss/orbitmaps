#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds area search shards: one small search index per 0.25-degree cell, for searching anywhere.

The app downloads the shard under the map centre (and its neighbours on Wi-Fi) from
data.orbitmaps.in/v1/search/v2/ and searches it on the phone, so the server only learns which area
is searched, never the query. Shards use the same schema as the region index (build_sample_search.py)
and the same cell grid and paths as Valhalla's level-2 tiles: 2/000/818/660.sqlite.

Input is any OSM extract (a country, a continent or the planet). Needs osmium-tool and a Python
whose sqlite3 has FTS5. For the planet, run on a machine with enough disk (see docs/HOSTING.md).

  python pipeline/sample_region/build_search_shards.py INPUT.osm.pbf [OUTPUT_DIR]

Default output: pipeline/out/search-shards/v2/
"""

from __future__ import annotations

import json
import math
import shutil
import subprocess
import sys
from collections.abc import Iterable
from datetime import datetime, timezone
from pathlib import Path

from build_sample_search import (
    Place,
    build_index,
    index_meta,
    merge_streets,
    place_from_feature,
    read_features,
    require_fts5,
)
from fetch_sample_region import OUT_DIR, sha256_of

CELL_DEGREES = 0.25
COLUMNS = int(360 / CELL_DEGREES)
ROWS = int(180 / CELL_DEGREES)
LEVEL = 2  # Valhalla's level-2 grid, so the app reuses one tile path scheme
DEFAULT_OUTPUT = OUT_DIR / "search-shards" / "v2"


def cell_id(lat: float, lon: float) -> int:
    row = min(int(math.floor((lat + 90.0) / CELL_DEGREES)), ROWS - 1)
    column = min(int(math.floor((lon + 180.0) / CELL_DEGREES)), COLUMNS - 1)
    return row * COLUMNS + column


def cell_path(cell: int) -> str:
    """The shard's path, like Valhalla's tile paths: the id zero-padded to 9 digits in groups of 3."""
    digits = str(cell).zfill(9)
    return f"{LEVEL}/{digits[0:3]}/{digits[3:6]}/{digits[6:9]}.sqlite"


def bucket(places: Iterable[Place]) -> dict[int, list[Place]]:
    cells: dict[int, list[Place]] = {}
    for place in places:
        cells.setdefault(cell_id(place.lat, place.lon), []).append(place)
    return cells


def write_shards(cells: dict[int, list[Place]], out_dir: Path, source_sha256: str, built_at: datetime) -> int:
    """Writes one index per cell; streets are merged per cell. Returns the number of shards."""
    for cell, places in sorted(cells.items()):
        merged = sorted(merge_streets(places), key=lambda p: (-p.importance, p.name))
        meta = index_meta(len(merged), source_sha256, built_at, region=f"cell-{LEVEL}-{cell}")
        build_index(merged, out_dir / cell_path(cell), meta)
    return len(cells)


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    source = Path(argv[1])
    out_dir = Path(argv[2]) if len(argv) > 2 else DEFAULT_OUTPUT
    if shutil.which("osmium") is None:
        raise SystemExit("osmium-tool is not on PATH")
    require_fts5()
    work = out_dir.parent / "shards-work"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    named = work / "named.osm.pbf"
    exported = work / "named.geojsonseq"
    subprocess.run(["osmium", "tags-filter", str(source), "nwr/name", "nwr/name:en", "--overwrite", "-o", str(named)], check=True)
    subprocess.run(
        ["osmium", "export", str(named), "-f", "geojsonseq", "--add-unique-id=type_id", "--overwrite", "-o", str(exported)],
        check=True,
    )
    places = (place_from_feature(feature) for feature in read_features(exported))
    count = write_shards(bucket(p for p in places if p is not None), out_dir, sha256_of(source), datetime.now(timezone.utc))
    shutil.rmtree(work)
    print(f"Wrote {count} shards to {out_dir}")
    print(json.dumps({"shards": count, "source": source.name}))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
