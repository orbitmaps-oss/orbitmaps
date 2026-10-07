#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fails if a tracked file looks like a secret, signing key or private IDE/local config.

Usage: python scripts/check_forbidden_files.py [repo_root]
"""

from __future__ import annotations

import fnmatch
import subprocess
import sys
from pathlib import Path, PurePosixPath

from policy import REPO_ROOT

FORBIDDEN_NAMES = [
    "*.jks",
    "*.keystore",
    "*.p12",
    "*.pem",
    "*.key",
    "keystore.properties",
    "signing.properties",
    "google-services.json",
    "GoogleService-Info.plist",
    ".env",
    ".env.*",
    ".dev.vars",
    ".dev.vars.*",
    "secrets.*",
    "local.properties",
    "*.iml",
]
FORBIDDEN_DIRS = {".idea", ".wrangler"}


def tracked_files(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=root,
        check=True,
        capture_output=True,
    )
    return [name for name in result.stdout.decode("utf-8").split("\0") if name]


def forbidden(paths: list[str]) -> list[str]:
    hits = []
    for name in paths:
        path = PurePosixPath(name)
        if FORBIDDEN_DIRS.intersection(path.parts[:-1]) or any(
            fnmatch.fnmatchcase(path.name, pattern) for pattern in FORBIDDEN_NAMES
        ):
            hits.append(name)
    return hits


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else REPO_ROOT
    hits = forbidden(tracked_files(root))
    for name in hits:
        print(f"ERROR: {name} must not be committed (secret, signing key or local/IDE file)")
    if hits:
        print("\nRemove it from git (git rm --cached <file>) and rotate any secret it contained.")
        return 1
    print("Forbidden files: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
