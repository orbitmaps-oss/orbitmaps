#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fails if the app could reach the network from its bundled map assets or manifest.

1. Every file under styles/bundled/ (style JSON, glyph PBFs, sprite PNGs, read as raw bytes) must not
   contain "http://" or "https://". Licence texts that ship next to the assets are skipped, because
   MapLibre never loads them.
2. With --manifest, each merged AndroidManifest.xml must not request android.permission.INTERNET.

Usage: python scripts/check_offline.py [--manifest merged AndroidManifest.xml ...]
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from policy import REPO_ROOT

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
INTERNET = "android.permission.INTERNET"
REMOTE_URL = re.compile(rb"https?://", re.IGNORECASE)
LICENCE_NAMES = re.compile(r"^(LICEN[CS]E.*\.(md|txt)|OFL\.txt)$", re.IGNORECASE)


def remote_url_errors(assets: Path) -> list[str]:
    if not assets.is_dir():
        return [f"{assets} not found"]
    errors = []
    for path in sorted(p for p in assets.rglob("*") if p.is_file()):
        if LICENCE_NAMES.match(path.name):
            continue
        if REMOTE_URL.search(path.read_bytes()):
            errors.append(f"{path.relative_to(assets).as_posix()} contains an http(s) URL; bundled map assets must be offline")
    return errors


def internet_errors(manifests: list[Path]) -> list[str]:
    errors = []
    for manifest in manifests:
        root = ET.parse(manifest).getroot()
        requested = {
            element.get(f"{ANDROID_NS}name")
            for tag in ("uses-permission", "uses-permission-sdk-23")
            for element in root.iter(tag)
        }
        if INTERNET in requested:
            errors.append(
                f"{manifest} requests {INTERNET}. The app is offline-only for now: remove it with "
                'tools:node="remove". Region-pack downloads will add it back later, with a reason in '
                "config/permissions-allowlist.txt"
            )
    return errors


def main(argv: list[str]) -> int:
    args = argv[1:]
    manifests: list[Path] = []
    if args:
        if args[0] != "--manifest" or len(args) < 2:
            print("usage: check_offline.py [--manifest AndroidManifest.xml ...]")
            return 2
        manifests = [Path(a) for a in args[1:]]
    errors = remote_url_errors(REPO_ROOT / "styles" / "bundled") + internet_errors(manifests)
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        return 1
    print(f"Offline policy: OK (bundled assets, {len(manifests)} manifest(s) checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
