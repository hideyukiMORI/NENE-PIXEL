package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class KotlinLibraryPluginTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `plugin applies the complete Kotlin library convention`() {
        writeFixture()

        runner("check", "--write-locks").build()
        val lockedBuild = runner("check").build()

        assertSuccessfulTask(lockedBuild, ":compileKotlin")
        assertSuccessfulTask(lockedBuild, ":detekt")
        assertSuccessfulTask(lockedBuild, ":ktlintMainSourceSetCheck")
        assertSuccessfulTask(lockedBuild, ":test")
        assertTrue(Files.exists(projectDirectory.resolve("gradle.lockfile")))

        writeFile("src/main/kotlin/probe/Probe.kt", IMPLICIT_API_SOURCE)
        val rejectedBuild = runner("compileKotlin").buildAndFail()
        assertTrue(rejectedBuild.output.contains("Visibility must be specified in explicit API mode"))
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments(*arguments, "--no-configuration-cache", "--stacktrace")
            .withPluginClasspath()

    private fun assertSuccessfulTask(
        result: BuildResult,
        path: String,
    ) {
        val task = result.task(path)
        assertNotNull(task, "Expected task $path to run.")
        requireNotNull(task)
        assertTrue(task.outcome in SUCCESSFUL_OUTCOMES, "$path ended as ${task.outcome}.")
    }

    private fun writeFixture() {
        writeFile("settings.gradle.kts", SETTINGS)
        writeFile("gradle/libs.versions.toml", VERSION_CATALOG)
        writeFile("config/detekt/detekt.yml", DETEKT_CONFIG)
        writeFile("build.gradle.kts", BUILD_FILE)
        writeFile("src/main/kotlin/probe/Probe.kt", KOTLIN_SOURCE)
    }

    private fun writeFile(
        relativePath: String,
        content: String,
    ) {
        val file = projectDirectory.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content.trimIndent() + System.lineSeparator())
    }

    private companion object {
        val SUCCESSFUL_OUTCOMES: Set<TaskOutcome> =
            setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE)

        const val SETTINGS: String = """
            dependencyResolutionManagement {
                repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "kotlin-library-convention-fixture"
        """

        const val VERSION_CATALOG: String = """
            [versions]
            detekt = "2.0.0-alpha.6"
            ktlint-engine = "1.8.0"
            junit = "6.1.2"
        """

        const val DETEKT_CONFIG: String = """
            config:
              validation: true
              warningsAsErrors: true
        """

        const val BUILD_FILE: String = """
            plugins {
                id("nene.kotlin-library")
            }
        """

        const val KOTLIN_SOURCE: String = """
            package probe

            public class Probe
        """

        const val IMPLICIT_API_SOURCE: String = """
            package probe

            class Probe
        """
    }
}
