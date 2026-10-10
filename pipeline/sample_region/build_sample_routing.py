#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds Valhalla routing tiles for the Panaji (Goa) sample region.

Runs in the "Sample region data" GitHub Actions workflow (.github/workflows/sample-region-data.yml), on
Linux with Docker and osmium-tool installed. It can also run on any Linux or WSL machine with both.

Steps:
  1. Download the pinned, dated Geofabrik India Western Zone extract (OSM data, ODbL) and check it
     against the MD5 Geofabrik publishes. Its SHA-256 goes into the manifest.
  2. Cut it to the same box as the map sample (fetch_sample_region.BBOX) with osmium.
  3. Build tiles and pack them into one tar with the pinned Valhalla image. The version must match
     the Valhalla inside valhalla-mobile (VALHALLA_VERSION), or the app can't read the tiles.
  4. Keep the generated valhalla.json: the app fills in its on-device paths and passes it to the
     engine, so the config always matches the Valhalla version that built the tiles.
  5. Write routing-manifest.json with versions, sizes, checksums and the licence.

Outputs (gitignored):
  pipeline/out/sample-region/assets/regions/panaji-routing.tar   (bundled in debug builds)
  pipeline/out/sample-region/assets/regions/panaji-routing.json  (bundled in debug builds)
  pipeline/out/sample-region/routing-manifest.json

Usage: python pipeline/sample_region/build_sample_routing.py
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import urllib.request
import hashlib
from datetime import datetime, timezone
from pathlib import Path

from fetch_sample_region import BBOX, OUT_DIR, sha256_of

# A dated extract, never "latest". Geofabrik keeps each 1 January file for years (2022 onwards are
# still there), so the build stays reproducible. To refresh the data, pin a newer 1 January file
# and its MD5 from https://download.geofabrik.de/asia/india/<file>.md5.
SOURCE_URL = "https://download.geofabrik.de/asia/india/western-zone-260101.osm.pbf"
SOURCE_MD5 = "d62746c49b4ba9277e1e6de4b5afad70"

# valhalla-mobile 0.6.3 builds Valhalla at commit e2f017b, the 3.6.3 release. Tiles must come from
# the same version. Pinned by digest: the amd64 image of ghcr.io/valhalla/valhalla:3.6.3.
VALHALLA_VERSION = "3.6.3"
VALHALLA_IMAGE = (
    "ghcr.io/valhalla/valhalla:3.6.3-amd64"
    "@sha256:0cf1520c6a38b8a7e13a1931541e0ab6e9e42b64b4ca014293b6b8373d493160"
)

SAMPLE_DIR = OUT_DIR / "sample-region"
WORK_DIR = SAMPLE_DIR / "routing-work"
TILES_TAR = SAMPLE_DIR / "assets" / "regions" / "panaji-routing.tar"
CONFIG = SAMPLE_DIR / "assets" / "regions" / "panaji-routing.json"
MANIFEST = SAMPLE_DIR / "routing-manifest.json"
EXTRACT = SAMPLE_DIR / "panaji.osm.pbf"

LICENCE = "ODbL-1.0"
ATTRIBUTION = "© OpenStreetMap contributors"

# Inside the container, WORK_DIR is mounted here.
CONTAINER_DATA = "/data"


def osmium_extract_command(source: Path, dest: Path) -> list[str]:
    """Cuts `source` to the sample box. `complete_ways` keeps roads that cross the edge whole."""
    bbox = ",".join(str(v) for v in BBOX)
    return [
        "osmium", "extract", "--bbox", bbox, "--strategy", "complete_ways",
        "--overwrite", "--output", str(dest), str(source),
    ]


def docker_command(work_dir: Path, *args: str, uid: int | None = None, gid: int | None = None) -> list[str]:
    """Runs one Valhalla tool in the pinned image, with the work directory mounted at /data."""
    command = ["docker", "run", "--rm", "--network", "none"]
    if uid is not None and gid is not None:
        # Files written to the mount belong to the calling user, not the image's user.
        command += ["--user", f"{uid}:{gid}"]
    command += ["-v", f"{work_dir.resolve()}:{CONTAINER_DATA}", "--entrypoint", args[0], VALHALLA_IMAGE]
    return command + list(args[1:])


# valhalla_build_config prints the configuration to standard output instead of writing a file.
CONFIG_STEP = "valhalla_build_config"


def stdout_file(step: list[str], work_dir: Path) -> Path | None:
    """Where a step's standard output must be saved: the config (printed by CONFIG_STEP) or nowhere."""
    return work_dir / "valhalla.json" if step[0] == CONFIG_STEP else None


def valhalla_steps(extract_name: str) -> list[list[str]]:
    """The Valhalla tool invocations, as argument lists for docker_command."""
    config = f"{CONTAINER_DATA}/valhalla.json"
    return [
        [
            "valhalla_build_config",
            "--mjolnir-tile-dir", f"{CONTAINER_DATA}/tiles",
            "--mjolnir-tile-extract", f"{CONTAINER_DATA}/tiles.tar",
            # No admin or timezone databases: fine for a prototype, routes still work.
            "--mjolnir-admin", f"{CONTAINER_DATA}/admins.sqlite",
            "--mjolnir-timezone", f"{CONTAINER_DATA}/timezones.sqlite",
        ],
        ["valhalla_build_tiles", "-c", config, f"{CONTAINER_DATA}/{extract_name}"],
        ["valhalla_build_extract", "-c", config, "-v"],
    ]


class ChecksumError(Exception):
    pass


def verify_md5(path: Path, expected: str) -> None:
    """Deletes the file and raises ChecksumError if its MD5 isn't the one Geofabrik publishes."""
    digest = hashlib.md5(usedforsecurity=False)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    if digest.hexdigest() != expected:
        path.unlink()
        raise ChecksumError(f"{path.name}: MD5 is {digest.hexdigest()}, expected {expected}; file deleted")


def manifest(source_sha256: str, source_modified: str, tiles: Path, built_at: datetime) -> dict:
    return {
        "region": "panaji",
        "bbox": list(BBOX),
        "built_at": built_at.astimezone(timezone.utc).isoformat(timespec="seconds"),
        "valhalla_version": VALHALLA_VERSION,
        "valhalla_image": VALHALLA_IMAGE,
        "source": {
            "url": SOURCE_URL,
            "md5": SOURCE_MD5,
            "sha256": source_sha256,
            "last_modified": source_modified,
        },
        "tiles": {"file": tiles.name, "size": tiles.stat().st_size, "sha256": sha256_of(tiles)},
        "licence": LICENCE,
        "attribution": ATTRIBUTION,
    }


def download(url: str, dest: Path) -> str:
    """Downloads to dest via a .part file and returns the Last-Modified header (or "")."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    partial = dest.with_name(dest.name + ".part")
    with urllib.request.urlopen(url, timeout=120) as response, partial.open("wb") as out:
        modified = response.headers.get("Last-Modified", "")
        while chunk := response.read(1 << 20):
            out.write(chunk)
    partial.replace(dest)
    return modified


def require_tools() -> None:
    missing = [tool for tool in ("docker", "osmium") if shutil.which(tool) is None]
    if missing:
        raise SystemExit(f"missing tools: {', '.join(missing)} (this script runs on Linux with Docker and osmium-tool)")


def main() -> int:
    require_tools()
    if WORK_DIR.exists():
        shutil.rmtree(WORK_DIR)
    WORK_DIR.mkdir(parents=True)

    source = WORK_DIR / "source.osm.pbf"
    print(f"Downloading {SOURCE_URL} ...")
    modified = download(SOURCE_URL, source)
    verify_md5(source, SOURCE_MD5)
    source_sha256 = sha256_of(source)
    print(f"Source SHA-256 {source_sha256}, Last-Modified {modified or 'unknown'}")

    extract = WORK_DIR / "panaji.osm.pbf"
    subprocess.run(osmium_extract_command(source, extract), check=True)
    source.unlink()  # 200+ MB; only the small extract is needed from here on

    uid, gid = (os.getuid(), os.getgid()) if hasattr(os, "getuid") else (None, None)
    for step in valhalla_steps(extract.name):
        print("Running", step[0], "...")
        command = docker_command(WORK_DIR, *step, uid=uid, gid=gid)
        target = stdout_file(step, WORK_DIR)
        if target is None:
            subprocess.run(command, check=True)
        else:
            with target.open("w", encoding="utf-8") as out:
                subprocess.run(command, check=True, stdout=out)
            if target.stat().st_size == 0:
                raise SystemExit(f"{step[0]} printed no configuration")

    built = WORK_DIR / "tiles.tar"
    if not built.is_file() or built.stat().st_size == 0:
        raise SystemExit("Valhalla produced no tile extract")
    TILES_TAR.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(built, TILES_TAR)
    shutil.copyfile(WORK_DIR / "valhalla.json", CONFIG)
    shutil.copyfile(extract, EXTRACT)

    data = manifest(source_sha256, modified, TILES_TAR, datetime.now(timezone.utc))
    MANIFEST.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    shutil.rmtree(WORK_DIR)
    print(f"Wrote {TILES_TAR} ({data['tiles']['size']} bytes, SHA-256 {data['tiles']['sha256']})")
    print(f"Wrote {CONFIG}")
    print(f"Wrote {MANIFEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
