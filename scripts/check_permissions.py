#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fails if the merged app manifest requests a permission that isn't explained.

Every <uses-permission> (and <uses-permission-sdk-23>) must be listed in
config/permissions-allowlist.txt with a reason. Signature-level permissions that the manifest
declares for itself (such as AndroidX's DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) are allowed.

Usage: python scripts/check_permissions.py [merged AndroidManifest.xml ...]
Default: the merged release manifest under app/build/intermediates.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from policy import REPO_ROOT

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
DEFAULT_MANIFEST_GLOB = "app/build/intermediates/merged_manifests/release/**/AndroidManifest.xml"


def parse_allowlist(path: Path) -> tuple[set[str], list[str]]:
    """Returns (allowed permissions, errors for entries without a reason)."""
    allowed, errors = set(), []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        name, _, reason = line.partition("#")
        name = name.strip()
        if not reason.strip():
            errors.append(f"{path.name}:{number}: {name} has no reason (add '# why it is needed')")
        allowed.add(name)
    return allowed, errors


def requested_permissions(manifest: Path) -> tuple[set[str], set[str]]:
    """Returns (requested permissions, signature permissions declared by the manifest itself)."""
    root = ET.parse(manifest).getroot()
    requested = {
        element.get(f"{ANDROID_NS}name")
        for tag in ("uses-permission", "uses-permission-sdk-23")
        for element in root.iter(tag)
    }
    self_declared = {
        element.get(f"{ANDROID_NS}name")
        for element in root.iter("permission")
        if "signature" in (element.get(f"{ANDROID_NS}protectionLevel") or "")
    }
    return {p for p in requested if p}, {p for p in self_declared if p}


def check(manifests: list[Path], allowlist: Path) -> list[str]:
    allowed, errors = parse_allowlist(allowlist)
    if not manifests:
        errors.append("no merged manifest found; build the app first (./gradlew :app:processReleaseManifest)")
    for manifest in manifests:
        requested, self_declared = requested_permissions(manifest)
        for permission in sorted(requested - self_declared - allowed):
            errors.append(
                f"{permission} is requested but not in config/permissions-allowlist.txt "
                "(add it with a reason and explain it in the PR)"
            )
    return errors


def main(argv: list[str]) -> int:
    manifests = [Path(a) for a in argv[1:]] or sorted(REPO_ROOT.glob(DEFAULT_MANIFEST_GLOB))
    errors = check(manifests, REPO_ROOT / "config" / "permissions-allowlist.txt")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        return 1
    print(f"Permission policy: OK ({len(manifests)} manifest(s) checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
