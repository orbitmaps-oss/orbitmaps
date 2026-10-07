#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fails if a dependency outside config/dependency-allowlist.txt is added.

Checks:
  * every module in every Gradle *.lockfile matches the allow-list; modules on a shipped runtime
    classpath must match the [app] section, everything else [app] or [build];
  * no module matches config/forbidden-dependencies.txt;
  * every allow-list pattern is documented (in backticks) in docs/THIRD_PARTY.md;
  * every GitHub Action used in .github/workflows is in the [actions] section and pinned to a
    full commit SHA.

Usage: python scripts/check_dependencies.py [repo_root]
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

from policy import REPO_ROOT, matches_any, read_lines

SECTIONS = ("app", "build", "actions")
SKIP_DIRS = {".git", ".gradle", "build", "node_modules", ".idea"}
TEST_CONFIGURATION = re.compile(r"(^test|UnitTest|AndroidTest|TestFixtures)")
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*['\"]?([^'\"\s#]+)", re.MULTILINE)
FULL_SHA = re.compile(r"^[0-9a-f]{40}$")


@dataclass
class AllowList:
    app: list[str] = field(default_factory=list)
    build: list[str] = field(default_factory=list)
    actions: list[str] = field(default_factory=list)

    def all_patterns(self) -> list[str]:
        return self.app + self.build + self.actions


def parse_allowlist(path: Path) -> AllowList:
    allow = AllowList()
    section = None
    for line in read_lines(path):
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            if section not in SECTIONS:
                raise ValueError(f"{path}: unknown section [{section}]")
            continue
        if section is None:
            raise ValueError(f"{path}: pattern '{line}' is outside a section")
        getattr(allow, section).append(line)
    return allow


def is_shipped(configuration: str) -> bool:
    """Runtime classpaths end up in the APK, except test ones."""
    return configuration.endswith(("RuntimeClasspath", "runtimeClasspath")) and not TEST_CONFIGURATION.search(
        configuration
    )


def find_lockfiles(root: Path) -> list[Path]:
    found = []
    for path in root.rglob("*.lockfile"):
        if not SKIP_DIRS.intersection(path.relative_to(root).parts[:-1]):
            found.append(path)
    return sorted(found)


def parse_lockfile(path: Path) -> list[tuple[str, list[str]]]:
    """Returns (group:artifact, configurations) for each locked module."""
    entries = []
    for line in read_lines(path):
        if line.startswith("empty="):
            continue
        coordinate, _, configurations = line.partition("=")
        parts = coordinate.split(":")
        if len(parts) != 3:
            raise ValueError(f"{path}: cannot parse '{line}'")
        entries.append((f"{parts[0]}:{parts[1]}", [c for c in configurations.split(",") if c]))
    return entries


def check_lockfiles(root: Path, allow: AllowList, forbidden: list[str]) -> list[str]:
    errors = []
    lockfiles = find_lockfiles(root)
    if not lockfiles:
        errors.append("no *.lockfile found; run ./gradlew dependencies --write-locks")
    for lockfile in lockfiles:
        where = lockfile.relative_to(root).as_posix()
        for module, configurations in parse_lockfile(lockfile):
            if matches_any(module, forbidden):
                errors.append(f"{where}: {module} is forbidden (config/forbidden-dependencies.txt)")
                continue
            shipped = [c for c in configurations if is_shipped(c)]
            if shipped:
                if not matches_any(module, allow.app):
                    errors.append(
                        f"{where}: {module} ships in the app ({', '.join(shipped)}) "
                        "but is not in the [app] section of config/dependency-allowlist.txt"
                    )
            elif not matches_any(module, allow.app + allow.build):
                errors.append(f"{where}: {module} is not in config/dependency-allowlist.txt")
    return errors


def check_third_party_doc(doc: Path, allow: AllowList) -> list[str]:
    text = doc.read_text(encoding="utf-8")
    return [
        f"allow-list pattern `{pattern}` is not documented in docs/THIRD_PARTY.md"
        for pattern in allow.all_patterns()
        if f"`{pattern}`" not in text
    ]


def check_workflows(root: Path, allow: AllowList) -> list[str]:
    errors = []
    workflows = root / ".github" / "workflows"
    if not workflows.is_dir():
        return errors
    for path in sorted(list(workflows.glob("*.yml")) + list(workflows.glob("*.yaml"))):
        where = path.relative_to(root).as_posix()
        for uses in USES.findall(path.read_text(encoding="utf-8")):
            if uses.startswith(("./", "docker://")):
                continue
            action, _, ref = uses.partition("@")
            owner_repo = "/".join(action.split("/")[:2])
            if owner_repo not in allow.actions:
                errors.append(f"{where}: action {owner_repo} is not in the [actions] allow-list")
            if not FULL_SHA.match(ref):
                errors.append(f"{where}: action {uses} must be pinned to a full commit SHA")
    return errors


def run(root: Path) -> list[str]:
    allow = parse_allowlist(root / "config" / "dependency-allowlist.txt")
    forbidden = read_lines(root / "config" / "forbidden-dependencies.txt")
    errors = []
    errors += check_lockfiles(root, allow, forbidden)
    errors += check_third_party_doc(root / "docs" / "THIRD_PARTY.md", allow)
    errors += check_workflows(root, allow)
    return errors


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else REPO_ROOT
    errors = run(root)
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(
            f"\n{len(errors)} dependency policy error(s). New dependencies must be open source, "
            "listed in docs/THIRD_PARTY.md and config/dependency-allowlist.txt (see CONTRIBUTING.md)."
        )
        return 1
    print("Dependency policy: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
