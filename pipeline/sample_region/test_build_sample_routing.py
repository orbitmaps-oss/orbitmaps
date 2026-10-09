# SPDX-License-Identifier: GPL-3.0-or-later

import hashlib
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import build_sample_routing as bsr
import fetch_sample_region as fsr


class BuildSampleRoutingTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def test_image_is_pinned_by_digest_to_the_valhalla_mobile_version(self):
        self.assertRegex(bsr.VALHALLA_IMAGE, r"^ghcr\.io/valhalla/valhalla:[\w.-]+@sha256:[0-9a-f]{64}$")
        self.assertIn(f":{bsr.VALHALLA_VERSION}-", bsr.VALHALLA_IMAGE)

    def test_source_is_a_dated_geofabrik_extract_with_a_pinned_md5(self):
        self.assertRegex(bsr.SOURCE_URL, r"^https://download\.geofabrik\.de/.+-\d{6}\.osm\.pbf$")
        self.assertNotIn("latest", bsr.SOURCE_URL)
        self.assertRegex(bsr.SOURCE_MD5, r"^[0-9a-f]{32}$")

    def test_matching_md5_keeps_the_file(self):
        path = self.root / "x.osm.pbf"
        path.write_bytes(b"osm")
        bsr.verify_md5(path, hashlib.md5(b"osm").hexdigest())
        self.assertTrue(path.exists())

    def test_wrong_md5_deletes_the_file(self):
        path = self.root / "x.osm.pbf"
        path.write_bytes(b"tampered")
        with self.assertRaises(bsr.ChecksumError):
            bsr.verify_md5(path, hashlib.md5(b"osm").hexdigest())
        self.assertFalse(path.exists())

    def test_extract_uses_the_same_box_as_the_map_sample(self):
        command = bsr.osmium_extract_command(Path("in.pbf"), Path("out.pbf"))
        self.assertEqual("osmium", command[0])
        self.assertIn(",".join(str(v) for v in fsr.BBOX), command)
        self.assertEqual("in.pbf", command[-1])

    def test_docker_runs_offline_with_the_pinned_image_and_mount(self):
        command = bsr.docker_command(self.root, "valhalla_build_tiles", "-c", "x.json", uid=1000, gid=1000)
        self.assertEqual(["docker", "run", "--rm", "--network", "none"], command[:5])
        self.assertIn("1000:1000", command)
        self.assertIn(f"{self.root.resolve()}:{bsr.CONTAINER_DATA}", command)
        image = command.index(bsr.VALHALLA_IMAGE)
        self.assertEqual("valhalla_build_tiles", command[image - 1])
        self.assertEqual(["-c", "x.json"], command[image + 1 :])

    def test_docker_without_uid_runs_as_the_image_user(self):
        self.assertNotIn("--user", bsr.docker_command(self.root, "valhalla_build_config"))

    def test_steps_build_config_then_tiles_then_one_tar(self):
        steps = bsr.valhalla_steps("panaji.osm.pbf")
        self.assertEqual(
            ["valhalla_build_config", "valhalla_build_tiles", "valhalla_build_extract"],
            [step[0] for step in steps],
        )
        self.assertIn(f"{bsr.CONTAINER_DATA}/tiles.tar", steps[0])
        self.assertEqual(f"{bsr.CONTAINER_DATA}/panaji.osm.pbf", steps[1][-1])

    def test_outputs_are_gitignored_and_the_tar_is_bundled_with_debug_assets(self):
        for path in (bsr.TILES_TAR, bsr.CONFIG, bsr.MANIFEST, bsr.WORK_DIR):
            self.assertEqual("out", path.relative_to(fsr.REPO_ROOT / "pipeline").parts[0])
        self.assertEqual(fsr.REGION_FILE.parent, bsr.TILES_TAR.parent)
        self.assertEqual(fsr.REGION_FILE.parent, bsr.CONFIG.parent)

    def test_manifest_records_versions_checksums_and_licence(self):
        tiles = self.root / "panaji-routing.tar"
        tiles.write_bytes(b"tiles")
        data = bsr.manifest("a" * 64, "Thu, 08 Oct 2026 20:21:06 GMT", tiles, datetime(2026, 10, 9, tzinfo=timezone.utc))
        self.assertEqual(bsr.VALHALLA_VERSION, data["valhalla_version"])
        self.assertEqual(
            {
                "url": bsr.SOURCE_URL,
                "md5": bsr.SOURCE_MD5,
                "sha256": "a" * 64,
                "last_modified": "Thu, 08 Oct 2026 20:21:06 GMT",
            },
            data["source"],
        )
        self.assertEqual({"file": "panaji-routing.tar", "size": 5, "sha256": fsr.sha256_of(tiles)}, data["tiles"])
        self.assertEqual("ODbL-1.0", data["licence"])
        self.assertEqual("© OpenStreetMap contributors", data["attribution"])
        self.assertEqual("2026-10-09T00:00:00+00:00", data["built_at"])
        json.dumps(data)  # serialisable


if __name__ == "__main__":
    unittest.main()
