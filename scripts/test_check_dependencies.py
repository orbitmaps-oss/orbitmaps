# SPDX-License-Identifier: GPL-3.0-or-later

import tempfile
import textwrap
import unittest
from pathlib import Path

import check_dependencies as cd

ALLOWLIST = """
[app]
androidx.*:*
org.jetbrains.kotlin:*

[build]
com.android.tools.*:*
junit:junit

[actions]
actions/checkout

[pipeline]
@protomaps/basemaps
"""

THIRD_PARTY = (
    "`androidx.*:*` `org.jetbrains.kotlin:*` `com.android.tools.*:*` `junit:junit` `actions/checkout` "
    "`@protomaps/basemaps`"
)

NPM_LOCK = """
{"lockfileVersion": 3, "packages": {"": {"name": "x"}, "node_modules/@protomaps/basemaps": {"version": "5.7.2"}}}
"""

FORBIDDEN = "com.google.firebase:*\ncom.google.android.gms:*\n"

GOOD_LOCK = """
# This is a Gradle generated file for dependency locking.
androidx.core:core:1.18.0=debugRuntimeClasspath,releaseRuntimeClasspath
org.jetbrains.kotlin:kotlin-stdlib:2.4.20=releaseRuntimeClasspath,kotlinCompilerClasspath
com.android.tools.lint:lint-api:32.4.1=lintChecks
junit:junit:4.13.2=debugUnitTestRuntimeClasspath,testRuntimeClasspath
empty=annotationProcessor
"""

SHA = "0123456789abcdef0123456789abcdef01234567"


class CheckDependenciesTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.write("config/dependency-allowlist.txt", ALLOWLIST)
        self.write("config/forbidden-dependencies.txt", FORBIDDEN)
        self.write("docs/THIRD_PARTY.md", THIRD_PARTY)
        self.write("app/gradle.lockfile", GOOD_LOCK)

    def tearDown(self):
        self._tmp.cleanup()

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(text), encoding="utf-8")

    def append_lock(self, line):
        self.write("app/gradle.lockfile", GOOD_LOCK + line + "\n")

    def test_valid_repository_passes(self):
        self.assertEqual([], cd.run(self.root))

    def test_unknown_dependency_fails(self):
        self.append_lock("com.example:unknown:1.0=debugCompileClasspath")
        errors = cd.run(self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("com.example:unknown is not in config/dependency-allowlist.txt", errors[0])

    def test_forbidden_dependency_fails_even_in_test_scope(self):
        self.append_lock("com.google.firebase:firebase-analytics:22.0.0=testRuntimeClasspath")
        errors = cd.run(self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("forbidden", errors[0])

    def test_build_only_dependency_cannot_ship(self):
        self.append_lock("com.android.tools:common:32.4.1=releaseRuntimeClasspath")
        errors = cd.run(self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("ships in the app", errors[0])

    def test_build_dependency_on_test_runtime_is_not_shipped(self):
        self.assertFalse(cd.is_shipped("debugUnitTestRuntimeClasspath"))
        self.assertFalse(cd.is_shipped("testRuntimeClasspath"))
        self.assertFalse(cd.is_shipped("debugAndroidTestRuntimeClasspath"))
        self.assertTrue(cd.is_shipped("releaseRuntimeClasspath"))
        self.assertTrue(cd.is_shipped("runtimeClasspath"))

    def test_missing_lockfiles_fail(self):
        (self.root / "app/gradle.lockfile").unlink()
        self.assertIn("no *.lockfile found", " ".join(cd.run(self.root)))

    def test_lockfiles_in_build_dirs_are_ignored(self):
        self.write("app/build/tmp/gradle.lockfile", "com.example:bad:1.0=releaseRuntimeClasspath\n")
        self.assertEqual([], cd.run(self.root))

    def test_undocumented_pattern_fails(self):
        self.write("docs/THIRD_PARTY.md", THIRD_PARTY.replace("`junit:junit`", ""))
        errors = cd.run(self.root)
        self.assertEqual(["allow-list pattern `junit:junit` is not documented in docs/THIRD_PARTY.md"], errors)

    def test_bare_group_pattern_matches_all_artifacts(self):
        self.write("config/dependency-allowlist.txt", ALLOWLIST.replace("junit:junit", "com.example"))
        self.write("docs/THIRD_PARTY.md", THIRD_PARTY + " `com.example`")
        self.write("app/gradle.lockfile", "com.example:anything:1.0=lintChecks\n")
        self.assertEqual([], cd.run(self.root))

    def test_pinned_allowed_action_passes(self):
        self.write(".github/workflows/ci.yml", f"steps:\n  - uses: actions/checkout@{SHA} # v5\n")
        self.assertEqual([], cd.run(self.root))

    def test_unpinned_action_fails(self):
        self.write(".github/workflows/ci.yml", "steps:\n  - uses: actions/checkout@v5\n")
        self.assertIn("pinned to a full commit SHA", " ".join(cd.run(self.root)))

    def test_unknown_action_fails(self):
        self.write(".github/workflows/ci.yml", f"steps:\n  - uses: someone/thing/sub@{SHA}\n")
        self.assertIn("someone/thing is not in the [actions] allow-list", " ".join(cd.run(self.root)))

    def test_allowed_npm_package_passes(self):
        self.write("pipeline/style/package-lock.json", NPM_LOCK)
        self.assertEqual([], cd.run(self.root))

    def test_unlisted_npm_package_fails(self):
        self.write("pipeline/style/package-lock.json", NPM_LOCK.replace("@protomaps/basemaps", "left-pad"))
        self.assertEqual(
            ["pipeline/style/package-lock.json: npm package left-pad is not in the [pipeline] allow-list"],
            cd.run(self.root),
        )

    def test_npm_lockfiles_in_node_modules_are_ignored(self):
        self.write("pipeline/style/node_modules/x/package-lock.json", NPM_LOCK.replace("@protomaps/basemaps", "y"))
        self.assertEqual([], cd.run(self.root))

    def test_undocumented_pipeline_pattern_fails(self):
        self.write("docs/THIRD_PARTY.md", THIRD_PARTY.replace("`@protomaps/basemaps`", ""))
        self.assertIn("`@protomaps/basemaps` is not documented", " ".join(cd.run(self.root)))

    def test_pattern_outside_section_is_rejected(self):
        self.write("config/dependency-allowlist.txt", "androidx.*:*\n")
        with self.assertRaises(ValueError):
            cd.run(self.root)


if __name__ == "__main__":
    unittest.main()
