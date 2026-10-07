# SPDX-License-Identifier: GPL-3.0-or-later

import unittest

import check_forbidden_files as cff


class CheckForbiddenFilesTest(unittest.TestCase):
    def test_secrets_and_keys_are_forbidden(self):
        paths = [
            "app/google-services.json",
            "release.jks",
            "app/upload.keystore",
            "keystore.properties",
            ".env",
            ".env.production",
            "pipeline/workers/.dev.vars",
            "local.properties",
            ".idea/workspace.xml",
            "app/app.iml",
        ]
        self.assertEqual(paths, cff.forbidden(paths))

    def test_normal_files_are_allowed(self):
        paths = [
            "README.md",
            "app/build.gradle.kts",
            "gradle/wrapper/gradle-wrapper.properties",
            "docs/environment.md",
            "app/src/main/res/values/strings.xml",
        ]
        self.assertEqual([], cff.forbidden(paths))


if __name__ == "__main__":
    unittest.main()
