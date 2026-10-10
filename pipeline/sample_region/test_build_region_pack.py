# SPDX-License-Identifier: GPL-3.0-or-later

import json
import tempfile
import unittest
from datetime import date
from pathlib import Path

import build_region_pack as brp
import fetch_sample_region as fsr


class RegionPackTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.sources = {}
        for role, content in {"map": b"PMTiles-map", "routing": b"tar", "routing_config": b"{}", "search": b"sqlite"}.items():
            path = self.root / f"in-{role}"
            path.write_bytes(content)
            self.sources[role] = path

    def tearDown(self):
        self._tmp.cleanup()

    def manifest(self, **overrides):
        args = {"region_id": "goa", "name": "Goa", "bbox": [73.6, 14.9, 74.4, 15.9], "sources": self.sources, "built": date(2026, 10, 10)}
        args.update(overrides)
        return brp.manifest_for(**args)

    def test_manifest_lists_every_file_with_size_and_sha256(self):
        manifest = self.manifest()
        self.assertEqual(brp.FORMAT_VERSION, manifest["version"])
        self.assertEqual("2026-10-10", manifest["built"])
        self.assertEqual(["map.pmtiles", "routing.tar", "routing.json", "search.sqlite"], [f["name"] for f in manifest["files"]])
        by_role = {f["role"]: f for f in manifest["files"]}
        self.assertEqual(len(b"PMTiles-map"), by_role["map"]["size"])
        self.assertEqual(fsr.sha256_of(self.sources["map"]), by_role["map"]["sha256"])
        self.assertEqual("ODbL-1.0", manifest["licence"])
        self.assertEqual("© OpenStreetMap contributors", manifest["attribution"])

    def test_a_missing_or_empty_file_is_refused(self):
        del self.sources["search"]
        with self.assertRaises(SystemExit):
            self.manifest()
        self.sources["search"] = self.root / "empty"
        self.sources["search"].write_bytes(b"")
        with self.assertRaises(SystemExit):
            self.manifest()

    def test_ids_and_boxes_are_checked(self):
        for bad in ("Goa", "goa india", "../x", "-goa", ""):
            with self.assertRaises(SystemExit, msg=bad):
                brp.validate_id(bad)
        self.assertEqual("goa-north", brp.validate_id("goa-north"))
        self.assertEqual([73.6, 14.9, 74.4, 15.9], brp.parse_bbox("73.6,14.9,74.4,15.9"))
        for bad in ("74,15,73,16", "1,2,3", "a,b,c,d", "-200,0,1,1", "0,10,1,5"):
            with self.assertRaises(SystemExit, msg=bad):
                brp.parse_bbox(bad)

    def test_pack_and_index_are_written_and_merged(self):
        out = self.root / "out"
        brp.write_pack(self.manifest(), self.sources, out)
        brp.write_pack(self.manifest(region_id="alpha", name="Alpha"), self.sources, out)
        brp.write_pack(self.manifest(name="Goa and coast"), self.sources, out)  # replaces goa
        index = json.loads((out / "index.json").read_text(encoding="utf-8"))
        self.assertEqual(["alpha", "goa"], [r["id"] for r in index["regions"]])
        goa = index["regions"][1]
        self.assertEqual("Goa and coast", goa["name"])
        self.assertEqual("goa/manifest.json", goa["manifest"])
        self.assertEqual(len(b"PMTiles-map") + 3 + 2 + 6, goa["size"])
        self.assertEqual(
            {"manifest.json", "map.pmtiles", "routing.tar", "routing.json", "search.sqlite"},
            {p.name for p in (out / "goa").iterdir()},
        )
        self.assertEqual(b"tar", (out / "goa" / "routing.tar").read_bytes())

    def test_sample_pack_uses_the_sample_region_box(self):
        self.assertEqual(list(fsr.BBOX), [73.734, 15.401, 73.921, 15.581])


if __name__ == "__main__":
    unittest.main()
