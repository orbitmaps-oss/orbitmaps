// SPDX-License-Identifier: GPL-3.0-or-later

// Lock the plugin classpath too, so build tooling is covered by the dependency allow-list check.
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencyLocking {
        lockAllConfigurations()
    }
}

// Local copy of CI (.github/workflows/ci.yml), so a pull request can be checked before pushing.
// Run `./gradlew checkAll`, or "Check all" in Android Studio's run configurations (.run/).
// The policy scripts need Python 3: "python" on Windows, "python3" elsewhere, or -Ppython=<path>.
val python =
    providers.gradleProperty("python").getOrElse(
        if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
    )

// Each check is an Exec task running one command line; arguments are separated by spaces.
fun registerPython(
    name: String,
    description: String,
    arguments: String,
) = tasks.register<Exec>(name) {
    group = "orbitmaps"
    this.description = description
    workingDir = rootDir
    commandLine(listOf(python) + arguments.split(" "))
}

val policyScriptTests =
    registerPython(
        "policyScriptTests",
        "Unit tests for the CI policy scripts",
        "-m unittest discover -s scripts -p test_*.py",
    )
val pipelineTests =
    registerPython(
        "pipelineTests",
        "Unit tests for the pipeline scripts",
        "-m unittest discover -s pipeline/sample_region -p test_*.py",
    )
val checkDependencyAllowlist =
    registerPython(
        "checkDependencyAllowlist",
        "Dependencies are on the allow-list",
        "scripts/check_dependencies.py",
    )
val checkForbiddenFiles =
    registerPython(
        "checkForbiddenFiles",
        "No secrets, keystores or IDE files are tracked",
        "scripts/check_forbidden_files.py",
    )
val checkOfflineAssets =
    registerPython(
        "checkOfflineAssets",
        "No remote URLs in bundled map assets",
        "scripts/check_offline.py",
    )

// The merged manifests that CI checks (one per build type).
val mergedManifests =
    listOf("debug", "release").joinToString(" ") { variant ->
        val task = "process${variant.replaceFirstChar(Char::uppercase)}Manifest"
        "app/build/intermediates/merged_manifests/$variant/$task/AndroidManifest.xml"
    }
val checkManifestPermissions =
    registerPython(
        "checkManifestPermissions",
        "Every permission in the merged manifests is on the permissions allow-list",
        "scripts/check_permissions.py $mergedManifests",
    )
val checkManifestOffline =
    registerPython(
        "checkManifestOffline",
        "No INTERNET permission in the merged manifests",
        "scripts/check_offline.py --manifest $mergedManifests",
    )
listOf(checkManifestPermissions, checkManifestOffline).forEach {
    it.configure { dependsOn(":app:processDebugManifest", ":app:processReleaseManifest") }
}

tasks.register("policyChecks") {
    group = "orbitmaps"
    description = "The CI policy checks that need no build (fast)"
    dependsOn(policyScriptTests, pipelineTests, checkDependencyAllowlist, checkForbiddenFiles, checkOfflineAssets)
}

tasks.register("checkAll") {
    group = "orbitmaps"
    description = "Everything CI checks: policy, build, unit tests, Android Lint, ktlint and manifests"
    dependsOn(
        "policyChecks",
        ":app:assembleDebug",
        ":app:assembleRelease",
        ":app:testDebugUnitTest",
        ":core:model:test",
        ":app:lintDebug",
        ":core:model:lint",
        checkManifestPermissions,
        checkManifestOffline,
    )
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
}

registerPython(
    "fetchSampleRegion",
    "Download the pinned Panaji sample region for debug builds (needs network)",
    "-I pipeline/sample_region/fetch_sample_region.py",
)
