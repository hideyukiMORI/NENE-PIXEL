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

internal class AndroidComposePluginTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `plugin applies the complete Android Compose library convention`() {
        writeFixture()

        val conventionBuild = runner("check", "--write-locks").build()
        val lockedBuild = runner("compileDebugKotlin").build()

        assertSuccessfulTask(conventionBuild, ":compileDebugKotlin")
        assertSuccessfulTask(conventionBuild, ":detekt")
        assertSuccessfulTask(conventionBuild, ":ktlintMainSourceSetCheck")
        assertSuccessfulTask(conventionBuild, ":lintDebug")
        assertSuccessfulTask(conventionBuild, ":testDebugUnitTest")
        assertSuccessfulTask(lockedBuild, ":compileDebugKotlin")
        assertTrue(Files.exists(projectDirectory.resolve("gradle.lockfile")))

        writeFile("src/main/kotlin/probe/Probe.kt", IMPLICIT_API_SOURCE)
        val rejectedBuild = runner("compileDebugKotlin").buildAndFail()
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
        writeFile("local.properties", "sdk.dir=${escapedAndroidSdkPath()}")
        writeFile("src/main/kotlin/probe/Probe.kt", KOTLIN_SOURCE)
    }

    private fun escapedAndroidSdkPath(): String {
        val sdkPath = requireNotNull(System.getenv("ANDROID_HOME")) { "ANDROID_HOME is required for Android tests." }
        return sdkPath.replace("\\", "\\\\").replace(":", "\\:")
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
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "android-compose-convention-fixture"
        """

        const val VERSION_CATALOG: String = """
            [versions]
            compose-bom = "2026.08.00"
            detekt = "2.0.0-alpha.6"
            junit = "6.1.2"
            ktlint-engine = "1.8.0"

            [libraries]
            compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
            compose-runtime = { module = "androidx.compose.runtime:runtime" }
        """

        const val DETEKT_CONFIG: String = """
            config:
              validation: true
              warningsAsErrors: true
        """

        const val BUILD_FILE: String = """
            import com.android.build.api.dsl.LibraryExtension

            plugins {
                id("com.android.library")
                id("nene.android-compose")
            }

            extensions.configure<LibraryExtension> {
                namespace = "probe"
            }

            dependencies {
                implementation(platform(libs.compose.bom))
                implementation(libs.compose.runtime)
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
