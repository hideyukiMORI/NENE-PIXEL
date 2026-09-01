package io.github.hideyukimori.nenepixel.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

internal fun Project.configureStrictDependencyLocking() {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

internal fun Project.configureKtlint(
    version: String,
    android: Boolean,
) {
    extensions.configure<KtlintExtension> {
        this.version.set(version)
        this.android.set(android)
        ignoreFailures.set(false)
        outputToConsole.set(true)
        relative.set(true)
    }
}

internal fun Project.configureDetekt(version: String) {
    extensions.configure<DetektExtension> {
        toolVersion.set(version)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig.set(true)
        allRules.set(false)
        parallel.set(true)
        ignoreFailures.set(false)
        failOnSeverity.set(FailOnSeverity.Warning)
        basePath.set(rootProject.projectDir)
    }
}

internal fun VersionCatalog.requiredVersion(alias: String): String =
    findVersion(alias)
        .orElseThrow {
            IllegalStateException("Missing version catalog alias '$alias'.")
        }.requiredVersion
