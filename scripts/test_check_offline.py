# SPDX-License-Identifier: GPL-3.0-or-later

import tempfile
import unittest
from pathlib import Path

import check_offline as co

class CheckOfflineTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.assets = self.root / "bundled"
        (self.assets / "map" / "glyphs" / "Noto Sans Regular").mkdir(parents=True)
        (self.assets / "map" / "style.json").write_text(
            '{"glyphs": "asset://map/glyphs/{fontstack}/{range}.pbf", "sources": '
            '{"p": {"url": "pmtiles://file:///REGION_NOT_INSTALLED"}}}',
            encoding="utf-8",
        )
        (self.assets / "map" / "glyphs" / "Noto Sans Regular" / "0-255.pbf").write_bytes(b"\x0a\x00\xff")
        (self.assets / "map" / "glyphs" / "OFL.txt").write_text("See https://openfontlicense.org\n", encoding="utf-8")
        (self.assets / "map" / "LICENSE-protomaps-basemaps.md").write_text("https://example.org\n", encoding="utf-8")

    def tearDown(self):
        self._tmp.cleanup()

    def test_clean_assets_pass_and_licence_texts_are_skipped(self):
        self.assertEqual([], co.remote_url_errors(self.assets))

    def test_url_in_style_json_fails(self):
        (self.assets / "map" / "style.json").write_text('{"sprite": "https://tiles.example/sprite"}', encoding="utf-8")
        errors = co.remote_url_errors(self.assets)
        self.assertEqual(1, len(errors))
        self.assertIn("map/style.json", errors[0])

    def test_url_inside_binary_fails(self):
        (self.assets / "map" / "glyphs" / "Noto Sans Regular" / "0-255.pbf").write_bytes(b"\x00\x01HTTP://x\xff")
        errors = co.remote_url_errors(self.assets)
        self.assertEqual(1, len(errors))
        self.assertIn("0-255.pbf", errors[0])

    def test_missing_assets_dir_fails(self):
        self.assertEqual(1, len(co.remote_url_errors(self.root / "missing")))

    def test_arguments_are_rejected(self):
        self.assertEqual(2, co.main(["check_offline.py", "--manifest", "x.xml"]))


if __name__ == "__main__":
    unittest.main()
