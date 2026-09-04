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

internal class AndroidTestPluginTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `plugin applies the complete Android test convention`() {
        writeFixture()

        val conventionBuild = runner("check", "--write-locks").build()
        val lockedBuild = runner("compileDebugKotlin").build()

        assertSuccessfulTask(conventionBuild, ":compileDebugKotlin")
        assertSuccessfulTask(conventionBuild, ":detekt")
        assertSuccessfulTask(conventionBuild, ":ktlintMainSourceSetCheck")
        assertSuccessfulTask(lockedBuild, ":compileDebugKotlin")
        assertTrue(Files.exists(projectDirectory.resolve("gradle.lockfile")))
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
        writeFile("target/build.gradle.kts", TARGET_BUILD_FILE)
        writeFile("target/src/main/AndroidManifest.xml", TARGET_MANIFEST)
        writeFile("local.properties", "sdk.dir=${escapedAndroidSdkPath()}")
        writeFile("src/main/kotlin/probe/ProfileJourney.kt", KOTLIN_SOURCE)
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

            rootProject.name = "android-test-convention-fixture"
            include(":target")
        """

        const val VERSION_CATALOG: String = """
            [versions]
            detekt = "2.0.0-alpha.6"
            ktlint-engine = "1.8.0"
        """

        const val DETEKT_CONFIG: String = """
            config:
              validation: true
              warningsAsErrors: true
        """

        const val BUILD_FILE: String = """
            import com.android.build.api.dsl.TestExtension

            plugins {
                id("com.android.test")
                id("nene.android-test")
            }

            extensions.configure<TestExtension> {
                namespace = "probe.test"
                targetProjectPath = ":target"
            }
        """

        const val TARGET_BUILD_FILE: String = """
            import com.android.build.api.dsl.ApplicationExtension

            plugins {
                id("com.android.application")
            }

            extensions.configure<ApplicationExtension> {
                namespace = "probe.target"
                compileSdk = 37
                defaultConfig {
                    applicationId = "probe.target"
                    minSdk = 26
                    targetSdk = 37
                    versionCode = 1
                    versionName = "1.0"
                }
            }
        """

        const val TARGET_MANIFEST: String = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
        """

        const val KOTLIN_SOURCE: String = """
            package probe

            internal class ProfileJourney
        """
    }
}
