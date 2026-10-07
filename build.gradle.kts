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
