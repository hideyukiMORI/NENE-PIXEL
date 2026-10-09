package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class AndroidLibraryPluginTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `plugin applies the complete non-Compose Android library convention`() {
        writeFixture()

        val conventionBuild = runner("check", "--write-locks", LintPolicyProbe.TASK).build()
        val lockedBuild = runner("compileDebugKotlin").build()

        assertSuccessfulTask(conventionBuild, ":compileDebugKotlin")
        assertSuccessfulTask(conventionBuild, ":detekt")
        assertSuccessfulTask(conventionBuild, ":ktlintMainSourceSetCheck")
        assertSuccessfulTask(conventionBuild, ":lintDebug")
        assertSuccessfulTask(conventionBuild, ":testDebugUnitTest")
        assertSuccessfulTask(conventionBuild, ":${LintPolicyProbe.TASK}")
        LintPolicyProbe.assertAdvisoryDependencyChecks(conventionBuild)
        assertSuccessfulTask(lockedBuild, ":compileDebugKotlin")
        assertTrue(Files.exists(projectDirectory.resolve("gradle.lockfile")))

        writeFile("src/main/kotlin/probe/Probe.kt", IMPLICIT_API_SOURCE)
        val rejectedBuild = runner("compileDebugKotlin").buildAndFail()
        assertTrue(rejectedBuild.output.contains("Visibility must be specified in explicit API mode"))
    }

    @Test
    fun `typed detekt resolves Java test classes and rejects forbidden Java calls`() {
        writeFixture()
        // Release analyzed fixture JARs before JUnit removes the temporary directory on Windows.
        writeFile("gradle.properties", "detekt.use.worker.api=true")
        writeFile("config/detekt/detekt.yml", DETEKT_CONFIG + JAVA_CALL_RULE)
        writeFile("build.gradle.kts", BUILD_FILE + DETEKT_DIAGNOSTICS)
        writeFile("src/androidTest/java/probe/JavaAnswer.java", JAVA_TEST_SOURCE)
        writeFile(ANDROID_TEST_SOURCE_PATH, MIXED_TEST_SOURCE)

        val resolvedBuild = runner("detektDebugAndroidTest", "--write-locks").build()
        assertSuccessfulTask(resolvedBuild, ":detektDebugAndroidTest")
        assertFalse(resolvedBuild.output.contains("compiler errors found during analysis"), resolvedBuild.output)
        assertSuccessfulTask(resolvedBuild, ":compileDebugAndroidTestJavaWithJavac")

        writeFile(ANDROID_TEST_SOURCE_PATH, MIXED_TEST_SOURCE.replace("allowed()", "forbidden()"))
        val rejectedBuild = runner("detektDebugAndroidTest").buildAndFail()
        assertSuccessfulTask(rejectedBuild, ":compileDebugAndroidTestKotlin")
        assertFalse(rejectedBuild.output.contains("compiler errors found during analysis"), rejectedBuild.output)
        assertTrue(rejectedBuild.output.contains("ForbiddenMethodCall"), rejectedBuild.output)
        assertEquals(TaskOutcome.FAILED, rejectedBuild.task(":detektDebugAndroidTest")?.outcome)
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
        writeFile("build.gradle.kts", LintPolicyProbe.buildScript(BUILD_FILE))
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
        const val ANDROID_TEST_SOURCE_PATH: String = "src/androidTest/kotlin/probe/JavaCall.kt"

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

            rootProject.name = "android-library-convention-fixture"
        """

        const val VERSION_CATALOG: String = """
            [versions]
            detekt = "2.0.0-alpha.6"
            junit = "6.1.2"
            ktlint-engine = "1.8.0"
        """

        const val DETEKT_CONFIG: String = """
            config:
              validation: true
              warningsAsErrors: true
        """

        const val JAVA_CALL_RULE: String = """
            style:
              ForbiddenMethodCall:
                active: true
                methods: ['probe.JavaAnswer.forbidden']
        """

        const val DETEKT_DIAGNOSTICS: String = """
            tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
                debug.set(true)
            }
        """

        const val JAVA_TEST_SOURCE: String = """
            package probe;

            public final class JavaAnswer {
                public static int allowed() { return 1; }
                public static int forbidden() { return 2; }
            }
        """

        const val MIXED_TEST_SOURCE: String = """
            package probe

            internal fun answer(): Int = JavaAnswer.allowed()
        """

        const val BUILD_FILE: String = """
            import com.android.build.api.dsl.LibraryExtension

            plugins {
                id("com.android.library")
                id("nene.android-library")
            }

            extensions.configure<LibraryExtension> {
                namespace = "probe"
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
