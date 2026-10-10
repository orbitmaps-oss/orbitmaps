#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fails if the bundled map assets would make the app reach the network.

Every file under styles/bundled/ (style JSON, glyph PBFs, sprite PNGs, read as raw bytes) must not
contain "http://" or "https://", so the offline map never fetches anything. Licence texts that ship
next to the assets are skipped, because MapLibre never loads them. The online map is configured in
code, against the hosts in config/network-hosts.txt (scripts/check_network_hosts.py).

Usage: python scripts/check_offline.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from policy import REPO_ROOT

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


def main(argv: list[str]) -> int:
    if argv[1:]:
        print("usage: check_offline.py")
        return 2
    errors = remote_url_errors(REPO_ROOT / "styles" / "bundled")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        return 1
    print("Offline policy: OK (bundled map assets)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
