# SPDX-License-Identifier: GPL-3.0-or-later
"""Shared helpers for the CI policy checks (standard library only)."""

from __future__ import annotations

import fnmatch
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def read_lines(path: Path) -> list[str]:
    """Returns non-empty lines with comments (from '#') removed and whitespace stripped."""
    lines = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            lines.append(line)
    return lines


def normalise_pattern(pattern: str) -> str:
    """A bare group ('com.example') means every artifact in it ('com.example:*')."""
    return pattern if ":" in pattern else f"{pattern}:*"


def matches_any(module: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(module, normalise_pattern(p)) for p in patterns)
