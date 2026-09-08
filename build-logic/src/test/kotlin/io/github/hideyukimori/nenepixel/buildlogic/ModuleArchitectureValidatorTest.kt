package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ModuleArchitectureValidatorTest {
    @Test
    fun `canonical graph and build tooling dependencies are accepted`() {
        val violations =
            validate(
                modules =
                    setOf(
                        ":",
                        APP,
                        QUALITY,
                        BASELINE_PROFILE,
                        DOMAIN,
                        PIXEL,
                        APPLICATION,
                        PROJECT_FORMAT,
                        PRESENTATION,
                    ),
                moduleDependencies =
                    listOf(
                        dependency(PIXEL, "implementation", DOMAIN),
                        dependency(APPLICATION, "implementation", DOMAIN),
                        dependency(APPLICATION, "implementation", PIXEL),
                        dependency(PROJECT_FORMAT, "implementation", DOMAIN),
                        dependency(PRESENTATION, "implementation", APPLICATION),
                        dependency(APP, "implementation", PRESENTATION),
                        dependency(APP, "detektPlugins", QUALITY),
                        dependency(APP, "baselineProfile", BASELINE_PROFILE),
                        dependency(BASELINE_PROFILE, "testedApks", APP),
                    ),
            )

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `production dependency on Baseline Profile producer is rejected`() {
        val violations =
            validate(
                modules = setOf(":", APP, BASELINE_PROFILE),
                moduleDependencies = listOf(dependency(APP, "implementation", BASELINE_PROFILE)),
            )

        assertContains(violations, "ARC-002 prohibits implementation dependency on ':quality:baseline-profile'")
    }

    @Test
    fun `Baseline Profile configuration outside Android app is rejected`() {
        val violations =
            validate(
                modules = setOf(":", PRESENTATION, BASELINE_PROFILE),
                moduleDependencies = listOf(dependency(PRESENTATION, "baselineProfile", BASELINE_PROFILE)),
            )

        assertContains(violations, "ARC-002 prohibits baselineProfile dependency on ':quality:baseline-profile'")
    }

    @Test
    fun `tested application configuration outside Baseline Profile producer is rejected`() {
        val violations =
            validate(
                modules = setOf(":", PRESENTATION, APP),
                moduleDependencies = listOf(dependency(PRESENTATION, "testedApks", APP)),
            )

        assertContains(violations, "ARC-002 prohibits testedApks dependency on ':app:android'")
    }

    @Test
    fun `forbidden dependency direction is rejected`() {
        val violations =
            validate(
                modules = setOf(":", APP, QUALITY, DOMAIN),
                moduleDependencies = listOf(dependency(DOMAIN, "implementation", APP)),
            )

        assertContains(violations, "ARC-002 prohibits implementation dependency on ':app:android'")
    }

    @Test
    fun `module cycle is rejected`() {
        val violations =
            validate(
                modules = setOf(":", QUALITY, DOMAIN, PIXEL),
                moduleDependencies =
                    listOf(
                        dependency(DOMAIN, "implementation", PIXEL),
                        dependency(PIXEL, "implementation", DOMAIN),
                    ),
            )

        assertContains(violations, "ARC-002 prohibits module cycle")
    }

    @Test
    fun `Android dependency in core configuration is rejected`() {
        val violations =
            validate(
                modules = setOf(":", QUALITY, DOMAIN),
                externalDependencies =
                    listOf(
                        DeclaredExternalDependency(
                            source = DOMAIN,
                            configuration = "implementation",
                            group = "androidx.compose.runtime",
                            name = "runtime",
                        ),
                    ),
            )

        assertContains(violations, "ARC-003 prohibits implementation dependency")
    }

    @Test
    fun `domain production dependency outside Kotlin standard library is rejected`() {
        val violations =
            validate(
                modules = setOf(":", QUALITY, DOMAIN),
                externalDependencies =
                    listOf(externalDependency("implementation", "org.example", "unexpected-runtime")),
            )

        assertContains(violations, "ARC-003 permits only Kotlin standard library production dependencies")
    }

    @Test
    fun `domain standard library and test dependencies are accepted`() {
        val violations =
            validate(
                modules = setOf(":", QUALITY, DOMAIN),
                externalDependencies =
                    listOf(
                        externalDependency("implementation", "org.jetbrains.kotlin", "kotlin-stdlib"),
                        externalDependency("testImplementation", "org.junit.jupiter", "junit-jupiter"),
                    ),
            )

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    private fun validate(
        modules: Set<String>,
        moduleDependencies: List<DeclaredModuleDependency> = emptyList(),
        externalDependencies: List<DeclaredExternalDependency> = emptyList(),
    ): List<ArchitectureViolation> =
        ModuleArchitectureValidator(modules, moduleDependencies, externalDependencies).validate()

    private fun dependency(
        source: String,
        configuration: String,
        target: String,
    ): DeclaredModuleDependency = DeclaredModuleDependency(source, configuration, target)

    private fun externalDependency(
        configuration: String,
        group: String,
        name: String,
    ): DeclaredExternalDependency = DeclaredExternalDependency(DOMAIN, configuration, group, name)

    private fun assertContains(
        violations: List<ArchitectureViolation>,
        message: String,
    ) {
        assertTrue(violations.any { message in it.message }, violations.joinToString(separator = "\n"))
    }

    private companion object {
        const val APP = ":app:android"
        const val QUALITY = ":quality:architecture-rules"
        const val BASELINE_PROFILE = ":quality:baseline-profile"
        const val DOMAIN = ":core:domain"
        const val PIXEL = ":core:pixel-engine"
        const val APPLICATION = ":core:application"
        const val PROJECT_FORMAT = ":core:project-format"
        const val PRESENTATION = ":presentation:compose"
    }
}
