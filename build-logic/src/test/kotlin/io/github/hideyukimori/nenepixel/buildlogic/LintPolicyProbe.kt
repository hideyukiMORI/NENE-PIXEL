package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Reports the lint severity policy that an applied Android convention installs so that the
 * convention tests can assert it instead of trusting the plugin source.
 */
internal object LintPolicyProbe {
    const val TASK: String = "reportLintPolicy"

    fun buildScript(buildFile: String): String =
        buildFile.trimIndent() + System.lineSeparator() + System.lineSeparator() + PROBE.trimIndent()

    fun assertAdvisoryDependencyChecks(result: BuildResult) {
        assertEquals(
            "GradleDependency,NewerVersionAvailable",
            readPolicy(result, "informational"),
            "Only the remote-index dependency checks may be informational.",
        )
        assertEquals("true", readPolicy(result, "abortOnError"), "abortOnError must stay enabled.")
        assertEquals("true", readPolicy(result, "checkDependencies"), "checkDependencies must stay enabled.")
        assertEquals("true", readPolicy(result, "checkReleaseBuilds"), "checkReleaseBuilds must stay enabled.")
        assertEquals("true", readPolicy(result, "warningsAsErrors"), "warningsAsErrors must stay enabled.")
    }

    private fun readPolicy(
        result: BuildResult,
        key: String,
    ): String {
        val prefix = "$MARKER $key="
        val line =
            result.output
                .lineSequence()
                .map { it.trimEnd() }
                .firstOrNull { it.startsWith(prefix) }
        return requireNotNull(line) { "Probe output is missing \"$prefix\"." }.removePrefix(prefix)
    }

    private const val MARKER: String = "lint-policy"

    private const val PROBE: String = """
        tasks.register("reportLintPolicy") {
            val lint = project.extensions.getByType<com.android.build.api.dsl.CommonExtension>().lint
            val informational = lint.informational.sorted().joinToString(",")
            val abortOnError = lint.abortOnError
            val checkDependencies = lint.checkDependencies
            val checkReleaseBuilds = lint.checkReleaseBuilds
            val warningsAsErrors = lint.warningsAsErrors
            doLast {
                logger.lifecycle("lint-policy informational=" + informational)
                logger.lifecycle("lint-policy abortOnError=" + abortOnError)
                logger.lifecycle("lint-policy checkDependencies=" + checkDependencies)
                logger.lifecycle("lint-policy checkReleaseBuilds=" + checkReleaseBuilds)
                logger.lifecycle("lint-policy warningsAsErrors=" + warningsAsErrors)
            }
        }
    """
}
