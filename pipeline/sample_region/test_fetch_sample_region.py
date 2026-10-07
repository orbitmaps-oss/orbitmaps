# SPDX-License-Identifier: GPL-3.0-or-later

import hashlib
import io
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

import fetch_sample_region as fsr


class FetchSampleRegionTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def test_every_platform_is_pinned_to_the_version_with_a_sha256(self):
        for name, sha in fsr.PMTILES_ARCHIVES.values():
            self.assertIn(fsr.PMTILES_VERSION, name)
            self.assertRegex(sha, r"^[0-9a-f]{64}$")

    def test_windows_machine_names_map_to_archives(self):
        self.assertEqual("go-pmtiles_1.31.2_Windows_x86_64.zip", fsr.archive_for("Windows", "AMD64")[0])
        self.assertEqual("go-pmtiles_1.31.2_Windows_arm64.zip", fsr.archive_for("Windows", "ARM64")[0])
        self.assertEqual("go-pmtiles_1.31.2_Linux_arm64.tar.gz", fsr.archive_for("Linux", "aarch64")[0])

    def test_unknown_platform_fails(self):
        with self.assertRaises(SystemExit):
            fsr.archive_for("Plan9", "mips")

    def test_matching_checksum_keeps_file(self):
        path = self.root / "a.zip"
        path.write_bytes(b"pmtiles")
        fsr.verify_sha256(path, hashlib.sha256(b"pmtiles").hexdigest())
        self.assertTrue(path.exists())

    def test_wrong_checksum_deletes_file(self):
        path = self.root / "a.zip"
        path.write_bytes(b"tampered")
        with self.assertRaises(fsr.ChecksumError):
            fsr.verify_sha256(path, hashlib.sha256(b"pmtiles").hexdigest())
        self.assertFalse(path.exists())

    def test_extracts_only_the_binary_from_zip(self):
        archive = self.root / "x.zip"
        with zipfile.ZipFile(archive, "w") as zf:
            zf.writestr("pmtiles.exe", b"exe")
            zf.writestr("../evil.txt", b"no")
        out = self.root / "out"
        out.mkdir()
        self.assertEqual(b"exe", fsr.extract_binary(archive, out, "pmtiles.exe").read_bytes())
        self.assertEqual(["pmtiles.exe"], [p.name for p in out.iterdir()])
        self.assertFalse((self.root / "evil.txt").exists())

    def test_extracts_only_the_binary_from_tar(self):
        archive = self.root / "x.tar.gz"
        with tarfile.open(archive, "w:gz") as tf:
            info = tarfile.TarInfo("pmtiles")
            info.size = 3
            tf.addfile(info, io.BytesIO(b"bin"))
        out = self.root / "out"
        out.mkdir()
        self.assertEqual(b"bin", fsr.extract_binary(archive, out, "pmtiles").read_bytes())

    def test_build_is_a_pinned_dated_build(self):
        self.assertRegex(fsr.BUILD_URL, r"^https://build\.protomaps\.com/\d{8}\.pmtiles$")
        self.assertRegex(fsr.EXPECTED_SHA256, r"^[0-9a-f]{64}$")

    def test_region_output_is_gitignored_location(self):
        self.assertEqual("out", fsr.REGION_FILE.relative_to(fsr.REPO_ROOT / "pipeline").parts[0])


if __name__ == "__main__":
    unittest.main()