# SPDX-License-Identifier: GPL-3.0-or-later

import tempfile
import unittest
from pathlib import Path

import check_permissions as cp

MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="in.orbitmaps.app">
    <permission
        android:name="in.orbitmaps.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        android:protectionLevel="signature" />
    <uses-permission android:name="in.orbitmaps.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    {extra}
</manifest>
"""


class CheckPermissionsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.allowlist = self.root / "permissions-allowlist.txt"
        self.allowlist.write_text("# none\n", encoding="utf-8")

    def tearDown(self):
        self._tmp.cleanup()

    def manifest(self, extra=""):
        path = self.root / "AndroidManifest.xml"
        path.write_text(MANIFEST.format(extra=extra), encoding="utf-8")
        return path

    def test_self_declared_signature_permission_is_allowed(self):
        self.assertEqual([], cp.check([self.manifest()], self.allowlist))

    def test_unlisted_permission_fails(self):
        extra = '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />'
        errors = cp.check([self.manifest(extra)], self.allowlist)
        self.assertEqual(1, len(errors))
        self.assertIn("android.permission.ACCESS_FINE_LOCATION", errors[0])

    def test_sdk23_permission_is_checked_too(self):
        extra = '<uses-permission-sdk-23 android:name="android.permission.CAMERA" />'
        self.assertEqual(1, len(cp.check([self.manifest(extra)], self.allowlist)))

    def test_listed_permission_with_reason_passes(self):
        self.allowlist.write_text(
            "android.permission.ACCESS_FINE_LOCATION  # show position on the map; stays on device\n",
            encoding="utf-8",
        )
        extra = '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />'
        self.assertEqual([], cp.check([self.manifest(extra)], self.allowlist))

    def test_listed_permission_without_reason_fails(self):
        self.allowlist.write_text("android.permission.ACCESS_FINE_LOCATION\n", encoding="utf-8")
        extra = '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />'
        errors = cp.check([self.manifest(extra)], self.allowlist)
        self.assertEqual(1, len(errors))
        self.assertIn("has no reason", errors[0])

    def test_missing_manifest_fails(self):
        self.assertIn("no merged manifest found", cp.check([], self.allowlist)[0])


if __name__ == "__main__":
    unittest.main()
