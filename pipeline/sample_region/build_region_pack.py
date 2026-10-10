#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Turns a region's map, routing and search files into a hostable region pack.

A pack is one folder with fixed file names plus a manifest that lists every file with its size and
SHA-256, so the app can download it in pieces, verify it and refuse a damaged or half-finished copy:

    <out>/index.json                the catalogue of all packs (merged on every run)
    <out>/<id>/manifest.json        files, sizes, checksums, bounds, licence
    <out>/<id>/map.pmtiles          vector map, as MapLibre reads it
    <out>/<id>/routing.tar          Valhalla tile tar (valhalla_build_extract)
    <out>/<id>/routing.json         the Valhalla config the tiles were built with
    <out>/<id>/search.sqlite        FTS5 search index (build_sample_search.py)

Upload the whole <out> folder to R2 as v1/regions/ (docs/HOSTING.md). The format is read by
app/.../regions/RegionManifest.kt: change both together and bump FORMAT_VERSION.

Usage (the Panaji sample, from the files the "Sample region data" workflow produced):
    python pipeline/sample_region/build_region_pack.py --sample
Any region:
    python pipeline/sample_region/build_region_pack.py --id goa --name "Goa" \\
        --bbox 73.6,14.9,74.4,15.9 --map goa.pmtiles --routing goa.tar \\
        --routing-config goa.json --search goa.sqlite
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from datetime import date
from pathlib import Path

from fetch_sample_region import BBOX, OUT_DIR, sha256_of

FORMAT_VERSION = 1
LICENCE = "ODbL-1.0"
ATTRIBUTION = "© OpenStreetMap contributors"

# role -> the file name inside every pack (the app only accepts these)
FILES = {
    "map": "map.pmtiles",
    "routing": "routing.tar",
    "routing_config": "routing.json",
    "search": "search.sqlite",
}
ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{0,40}$")
DEFAULT_OUT = OUT_DIR / "packs" / "regions"
SAMPLE_DIR = OUT_DIR / "sample-region" / "assets" / "regions"


def validate_id(region_id: str) -> str:
    if not ID_PATTERN.match(region_id):
        raise SystemExit(f"region id {region_id!r} must be lower case letters, digits and '-' (max 41)")
    return region_id


def parse_bbox(text: str) -> list[float]:
    """west,south,east,north in degrees, checked."""
    try:
        west, south, east, north = (float(part) for part in text.split(","))
    except ValueError as error:
        raise SystemExit(f"bbox must be 'west,south,east,north': {error}") from error
    if not (-180 <= west < east <= 180 and -90 <= south < north <= 90):
        raise SystemExit(f"bbox {text!r} is not a valid box (west < east, south < north)")
    return [west, south, east, north]


def file_entry(role: str, path: Path) -> dict:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"{role}: {path} is missing or empty")
    return {"role": role, "name": FILES[role], "size": path.stat().st_size, "sha256": sha256_of(path)}


def manifest_for(region_id: str, name: str, bbox: list[float], sources: dict[str, Path], built: date) -> dict:
    """The manifest for a pack built from `sources` (role -> file). Every role is required."""
    missing = [role for role in FILES if role not in sources]
    if missing:
        raise SystemExit(f"missing files: {', '.join(missing)}")
    return {
        "version": FORMAT_VERSION,
        "id": validate_id(region_id),
        "name": name,
        "built": built.isoformat(),
        "bbox": bbox,
        "licence": LICENCE,
        "attribution": ATTRIBUTION,
        "files": [file_entry(role, sources[role]) for role in FILES],
    }


def index_entry(manifest: dict) -> dict:
    return {
        "id": manifest["id"],
        "name": manifest["name"],
        "bbox": manifest["bbox"],
        "size": sum(f["size"] for f in manifest["files"]),
        "built": manifest["built"],
        "manifest": f"{manifest['id']}/manifest.json",
    }


def merge_index(existing: dict | None, entry: dict) -> dict:
    """The catalogue with `entry` added or replaced, sorted by name."""
    regions = [r for r in (existing or {}).get("regions", []) if r.get("id") != entry["id"]]
    regions.append(entry)
    return {"version": FORMAT_VERSION, "regions": sorted(regions, key=lambda r: r["name"].lower())}


def write_pack(manifest: dict, sources: dict[str, Path], out_dir: Path) -> Path:
    pack = out_dir / manifest["id"]
    pack.mkdir(parents=True, exist_ok=True)
    for role, name in FILES.items():
        shutil.copyfile(sources[role], pack / name)
    (pack / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    index_path = out_dir / "index.json"
    existing = json.loads(index_path.read_text(encoding="utf-8")) if index_path.is_file() else None
    merged = merge_index(existing, index_entry(manifest))
    index_path.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return pack


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sample", action="store_true", help="build the Panaji sample pack from the workflow's files")
    parser.add_argument("--id")
    parser.add_argument("--name")
    parser.add_argument("--bbox", help="west,south,east,north")
    parser.add_argument("--map", type=Path)
    parser.add_argument("--routing", type=Path)
    parser.add_argument("--routing-config", type=Path)
    parser.add_argument("--search", type=Path)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    if args.sample:
        region_id, name = "panaji", "Panaji (sample)"
        bbox = list(BBOX)
        sources = {
            "map": SAMPLE_DIR / "panaji.pmtiles",
            "routing": SAMPLE_DIR / "panaji-routing.tar",
            "routing_config": SAMPLE_DIR / "panaji-routing.json",
            "search": SAMPLE_DIR / "panaji-search.sqlite",
        }
    else:
        if not (args.id and args.name and args.bbox and args.map and args.routing and args.routing_config and args.search):
            parser.error("give --sample, or all of --id --name --bbox --map --routing --routing-config --search")
        region_id, name, bbox = args.id, args.name, parse_bbox(args.bbox)
        sources = {
            "map": args.map,
            "routing": args.routing,
            "routing_config": args.routing_config,
            "search": args.search,
        }
    manifest = manifest_for(region_id, name, bbox, sources, date.today())
    pack = write_pack(manifest, sources, args.out)
    total = sum(f["size"] for f in manifest["files"])
    print(f"Wrote {pack} ({total} bytes in {len(manifest['files'])} files) and {args.out / 'index.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
