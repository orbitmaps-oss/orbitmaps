#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Downloads the pinned pmtiles CLI and extracts the Panaji (Goa) sample region.

The result, pipeline/out/sample-region/assets/regions/panaji.pmtiles, is gitignored. Debug builds
bundle it as an APK asset, and the app copies it into app storage on first launch.

Everything is pinned so the extract is reproducible:
  * pmtiles CLI (go-pmtiles) PMTILES_VERSION. Each release archive is checked against the
    SHA-256 below *before* it is unpacked. go-pmtiles publishes no checksums file. The hashes
    are the GitHub release asset digests, and each was checked by downloading the archive and
    hashing it again.
  * Protomaps Basemap build BUILD_URL. Protomaps keeps the builds from the last week plus the
    newest build of every tileset patch version. 20260811 is the only 4.15.1 build, so it
    stays available. If it is ever removed, pin the newest build of another patch version from
    https://build-metadata.protomaps.dev/builds.json and update EXPECTED_SHA256.
  * The bounding box, max zoom and the SHA-256 of the extract.

Only the tools folder and the output folder are written; nothing is installed system-wide.

Usage: python -I pipeline/sample_region/fetch_sample_region.py [--update-expected]
"""

from __future__ import annotations

import argparse
import hashlib
import platform
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = REPO_ROOT / "pipeline" / "out"

PMTILES_VERSION = "1.31.2"
PMTILES_RELEASE_URL = f"https://github.com/protomaps/go-pmtiles/releases/download/v{PMTILES_VERSION}"
# (system, machine) -> (archive name, SHA-256)
PMTILES_ARCHIVES = {
    ("Windows", "x86_64"): (
        "go-pmtiles_1.31.2_Windows_x86_64.zip",
        "a658baa4d7e55020aef6ca17bd9ff9faa1582671266b36f58c52db0ac8e785a1",
    ),
    ("Windows", "arm64"): (
        "go-pmtiles_1.31.2_Windows_arm64.zip",
        "8780a17453c63af757917a694cbbb50b943db89cc3f1b07e6fd62c1ff8e6963b",
    ),
    ("Linux", "x86_64"): (
        "go-pmtiles_1.31.2_Linux_x86_64.tar.gz",
        "3ed7dbf4ec2e6dfe5e25b6f70d1ffc932729f93c86db353bf514dd71010a312f",
    ),
    ("Linux", "arm64"): (
        "go-pmtiles_1.31.2_Linux_arm64.tar.gz",
        "f8bd47e7ea866863489cad588fbaf2f31f42e5821f7a03f009b3769f05801cb1",
    ),
    ("Darwin", "x86_64"): (
        "go-pmtiles-1.31.2_Darwin_x86_64.zip",
        "1f0dc02eee6c58312dd6c509faee1b5c32f0596568af1bf51f1b034e7a88a65b",
    ),
    ("Darwin", "arm64"): (
        "go-pmtiles-1.31.2_Darwin_arm64.zip",
        "40528f7f616fcbf91207cd48c8fc023d213f6d86c0cbf1f748732803d1880f3d",
    ),
}
MACHINE_ALIASES = {"amd64": "x86_64", "x86_64": "x86_64", "x64": "x86_64", "arm64": "arm64", "aarch64": "arm64"}

BUILD_URL = "https://build.protomaps.com/20260811.pmtiles"  # tileset 4.15.1
# About 10 km around Panaji, Goa (min lon, min lat, max lon, max lat).
BBOX = (73.734, 15.401, 73.921, 15.581)
MAX_ZOOM = 15
EXPECTED_SHA256 = "07ff9284fb2b18b29b80d8f309641563cfd7e28d553c4144c4ebe4f7882fe6f4"

REGION_FILE = OUT_DIR / "sample-region" / "assets" / "regions" / "panaji.pmtiles"


class ChecksumError(Exception):
    pass


def archive_for(system: str, machine: str) -> tuple[str, str]:
    """Returns (archive name, SHA-256) of the pmtiles CLI for this platform."""
    key = (system, MACHINE_ALIASES.get(machine.lower(), machine.lower()))
    if key not in PMTILES_ARCHIVES:
        raise SystemExit(f"no pinned pmtiles {PMTILES_VERSION} build for {system} {machine}")
    return PMTILES_ARCHIVES[key]


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_sha256(path: Path, expected: str) -> None:
    """Deletes the file and raises ChecksumError if its SHA-256 isn't the expected one."""
    actual = sha256_of(path)
    if actual != expected:
        path.unlink()
        raise ChecksumError(f"{path.name}: SHA-256 is {actual}, expected {expected}; file deleted")


def download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    partial = dest.with_name(dest.name + ".part")
    with urllib.request.urlopen(url, timeout=60) as response, partial.open("wb") as out:
        while chunk := response.read(1 << 20):
            out.write(chunk)
    partial.replace(dest)


def extract_binary(archive: Path, dest_dir: Path, binary: str) -> Path:
    """Extracts only the top-level `binary` member, never other paths, from a zip or tar.gz."""
    target = dest_dir / binary
    if archive.name.endswith(".zip"):
        with zipfile.ZipFile(archive) as zf:
            if binary not in zf.namelist():
                raise SystemExit(f"{archive.name} has no {binary}")
            target.write_bytes(zf.read(binary))
    else:
        with tarfile.open(archive, "r:gz") as tf:
            member = tf.getmember(binary)
            if not member.isfile():
                raise SystemExit(f"{archive.name}: {binary} is not a regular file")
            source = tf.extractfile(member)
            assert source is not None
            target.write_bytes(source.read())
    target.chmod(0o755)
    return target


def ensure_pmtiles() -> Path:
    system = platform.system()
    name, expected = archive_for(system, platform.machine())
    tools = OUT_DIR / "tools" / f"pmtiles-{PMTILES_VERSION}"
    binary = tools / ("pmtiles.exe" if system == "Windows" else "pmtiles")
    archive = tools / name
    if not binary.exists():
        if not archive.exists():
            print(f"Downloading {name} ...")
            download(f"{PMTILES_RELEASE_URL}/{name}", archive)
        verify_sha256(archive, expected)
        extract_binary(archive, tools, binary.name)
    version = subprocess.run([str(binary), "version"], check=True, capture_output=True, text=True).stdout
    if f"pmtiles {PMTILES_VERSION}," not in version:
        raise SystemExit(f"unexpected pmtiles version: {version.strip()}")
    return binary


def extract_region(pmtiles: Path, update_expected: bool) -> int:
    REGION_FILE.parent.mkdir(parents=True, exist_ok=True)
    partial = REGION_FILE.with_name(REGION_FILE.name + ".part")
    bbox = ",".join(str(v) for v in BBOX)
    print(f"Extracting {bbox} (z0-{MAX_ZOOM}) from {BUILD_URL} ...")
    subprocess.run(
        [str(pmtiles), "extract", BUILD_URL, str(partial), f"--bbox={bbox}", f"--maxzoom={MAX_ZOOM}"],
        check=True,
    )
    subprocess.run([str(pmtiles), "verify", str(partial)], check=True)
    actual = sha256_of(partial)
    if actual != EXPECTED_SHA256 and not update_expected:
        partial.unlink()
        print(f"ERROR: extract SHA-256 is {actual}, expected {EXPECTED_SHA256}.")
        print("If the pins were changed on purpose, run again with --update-expected and commit the new hash.")
        return 1
    partial.replace(REGION_FILE)
    print(f"Wrote {REGION_FILE.relative_to(REPO_ROOT).as_posix()} ({REGION_FILE.stat().st_size} bytes)")
    print(f"SHA-256 {actual}")
    if actual != EXPECTED_SHA256:
        print(f"Update EXPECTED_SHA256 in {Path(__file__).name} to the hash above.")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--update-expected", action="store_true", help="accept an extract whose SHA-256 differs from the pin"
    )
    args = parser.parse_args(argv)
    try:
        pmtiles = ensure_pmtiles()
    except ChecksumError as error:
        print(f"ERROR: {error}")
        return 1
    return extract_region(pmtiles, args.update_expected)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))