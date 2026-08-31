package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ModuleArchitectureValidatorTest {
    @Test
    fun `canonical graph and architecture tooling dependency are accepted`() {
        val violations =
            validate(
                modules = setOf(":", APP, QUALITY, DOMAIN, PIXEL, APPLICATION, PRESENTATION),
                moduleDependencies =
                    listOf(
                        dependency(PIXEL, "implementation", DOMAIN),
                        dependency(APPLICATION, "implementation", DOMAIN),
                        dependency(APPLICATION, "implementation", PIXEL),
                        dependency(PRESENTATION, "implementation", APPLICATION),
                        dependency(APP, "implementation", PRESENTATION),
                        dependency(APP, "detektPlugins", QUALITY),
                    ),
            )

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
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

    private fun assertContains(
        violations: List<ArchitectureViolation>,
        message: String,
    ) {
        assertTrue(violations.any { message in it.message }, violations.joinToString(separator = "\n"))
    }

    private companion object {
        const val APP = ":app:android"
        const val QUALITY = ":quality:architecture-rules"
        const val DOMAIN = ":core:domain"
        const val PIXEL = ":core:pixel-engine"
        const val APPLICATION = ":core:application"
        const val PRESENTATION = ":presentation:compose"
    }
}
