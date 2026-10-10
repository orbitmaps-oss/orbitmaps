# SPDX-License-Identifier: GPL-3.0-or-later

import tempfile
import unittest
from pathlib import Path

import check_network_hosts as cnh

HOSTS = """# comment
[fetch]
data.orbitmaps.in  # static files

[browser]
www.openstreetmap.org  # copyright link
"""


class CheckNetworkHostsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        (self.root / "config").mkdir()
        (self.root / "config" / "network-hosts.txt").write_text(HOSTS, encoding="utf-8")
        self.main = self.root / "app" / "src" / "main"
        self.main.mkdir(parents=True)

    def tearDown(self):
        self._tmp.cleanup()

    def write(self, relative, text):
        path = self.root / "app" / "src" / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def run_check(self):
        return cnh.main(["x", str(self.root)])

    def test_parses_sections_and_reasons(self):
        hosts, errors = cnh.parse_hosts(self.root / "config" / "network-hosts.txt")
        self.assertEqual([], errors)
        self.assertEqual({"data.orbitmaps.in": "static files"}, hosts.fetch)
        self.assertEqual({"www.openstreetmap.org"}, set(hosts.browser))

    def test_host_without_reason_or_section_is_an_error(self):
        path = self.root / "config" / "network-hosts.txt"
        path.write_text("loose.example  # x\n[fetch]\nno-reason.example\n[other]\n", encoding="utf-8")
        _, errors = cnh.parse_hosts(path)
        self.assertEqual(3, len(errors))

    def test_allowed_hosts_pass(self):
        self.write("main/kotlin/A.kt", 'val a = "pmtiles://https://data.orbitmaps.in/v1/world.pmtiles"\n'
                   'val b = "https://www.openstreetmap.org/copyright"\n')
        self.write("main/AndroidManifest.xml", '<manifest xmlns:android="http://schemas.android.com/apk/res/android"/>\n')
        self.assertEqual(0, self.run_check())

    def test_unknown_host_fails(self):
        self.write("main/kotlin/A.kt", 'val a = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"\n')
        files = cnh.source_files(self.root / "app" / "src")
        hosts, _ = cnh.parse_hosts(self.root / "config" / "network-hosts.txt")
        errors = cnh.url_errors(files, hosts, self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("tile.openstreetmap.org", errors[0])
        self.assertIn("A.kt:1", errors[0])

    def test_plain_http_fails_even_for_allowed_hosts(self):
        self.write("debug/kotlin/A.kt", 'val a = "http://data.orbitmaps.in/x"\n')
        self.assertEqual(1, self.run_check())

    def test_test_sources_are_not_scanned(self):
        self.write("test/kotlin/ATest.kt", 'val a = "http://localhost:8080/x"\n')
        self.assertEqual(0, self.run_check())


if __name__ == "__main__":
    unittest.main()
