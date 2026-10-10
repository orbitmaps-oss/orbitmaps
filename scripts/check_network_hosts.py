#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fails if app code contains a URL to a host that isn't in config/network-hosts.txt, or plain http.

Scans Kotlin and XML under app/src (not test sources). Android's XML namespace URIs
(http://schemas.android.com/...) are identifiers, not network addresses, and are skipped.

Usage: python scripts/check_network_hosts.py [repo_root]
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

from policy import REPO_ROOT

SECTIONS = ("fetch", "browser")
URL = re.compile(r"\b(https?)://([A-Za-z0-9.-]+)")
NAMESPACE_HOSTS = {"schemas.android.com"}
SOURCE_SUFFIXES = {".kt", ".kts", ".xml"}
TEST_DIRS = {"test", "androidTest"}


@dataclass
class Hosts:
    fetch: dict[str, str] = field(default_factory=dict)
    browser: dict[str, str] = field(default_factory=dict)

    def allowed(self) -> set[str]:
        return set(self.fetch) | set(self.browser)


def parse_hosts(path: Path) -> tuple[Hosts, list[str]]:
    """Returns the hosts per section and errors for lines without a reason or outside a section."""
    hosts, errors, section = Hosts(), [], None
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            if section not in SECTIONS:
                errors.append(f"{path.name}:{number}: unknown section [{section}]")
                section = None
            continue
        host, _, reason = line.partition("#")
        host = host.strip()
        if section is None:
            errors.append(f"{path.name}:{number}: {host} is outside a section")
        elif not reason.strip():
            errors.append(f"{path.name}:{number}: {host} has no reason (add '# why the app needs it')")
        else:
            getattr(hosts, section)[host] = reason.strip()
    return hosts, errors


def source_files(app_src: Path) -> list[Path]:
    return sorted(
        p for p in app_src.rglob("*")
        if p.is_file() and p.suffix in SOURCE_SUFFIXES and not TEST_DIRS.intersection(p.relative_to(app_src).parts)
    )


def url_errors(files: list[Path], hosts: Hosts, root: Path) -> list[str]:
    errors = []
    allowed = hosts.allowed()
    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        for number, line in enumerate(text.splitlines(), start=1):
            for scheme, host in URL.findall(line):
                if host in NAMESPACE_HOSTS:
                    continue
                where = f"{path.relative_to(root).as_posix()}:{number}"
                if scheme == "http":
                    errors.append(f"{where}: plain http://{host}; the app only uses HTTPS")
                elif host not in allowed:
                    errors.append(f"{where}: {host} is not in config/network-hosts.txt")
    return errors


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else REPO_ROOT
    hosts, errors = parse_hosts(root / "config" / "network-hosts.txt")
    errors += url_errors(source_files(root / "app" / "src"), hosts, root)
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print("\nAdd the host with a reason (and to PRIVACY.md if the app fetches from it), or remove the URL.")
        return 1
    print(f"Network hosts: OK ({len(hosts.fetch)} fetch, {len(hosts.browser)} browser-only)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
