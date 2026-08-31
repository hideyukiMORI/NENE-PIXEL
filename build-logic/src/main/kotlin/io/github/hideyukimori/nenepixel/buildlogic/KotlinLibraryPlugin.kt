package io.github.hideyukimori.nenepixel.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

public class KotlinLibraryPlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("org.jetbrains.kotlin.jvm")
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        val libraries = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        configureToolchains(target)
        configureDependencies(target)
        configureKotlin(target)
        configureKtlint(target, libraries.version("ktlint-engine"))
        configureDetekt(target, libraries.version("detekt"))
        configureTests(target, libraries.version("junit"))
    }

    private fun configureToolchains(target: Project) {
        target.extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(JAVA_TOOLCHAIN_VERSION))
            }
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    private fun configureDependencies(target: Project) {
        target.dependencyLocking {
            lockAllConfigurations()
            lockMode.set(LockMode.STRICT)
        }
    }

    private fun configureKotlin(target: Project) {
        target.extensions.configure<KotlinJvmProjectExtension> {
            explicitApi()
        }
        target.tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                allWarningsAsErrors.set(true)
                freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    private fun configureKtlint(
        target: Project,
        version: String,
    ) {
        target.extensions.configure<KtlintExtension> {
            this.version.set(version)
            ignoreFailures.set(false)
            outputToConsole.set(true)
            relative.set(true)
        }
    }

    private fun configureDetekt(
        target: Project,
        version: String,
    ) {
        target.extensions.configure<DetektExtension> {
            toolVersion.set(version)
            config.setFrom(target.rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig.set(true)
            allRules.set(false)
            parallel.set(true)
            ignoreFailures.set(false)
            failOnSeverity.set(FailOnSeverity.Warning)
            basePath.set(target.rootProject.projectDir)
        }
    }

    private fun configureTests(
        target: Project,
        junitVersion: String,
    ) {
        target.extensions.configure<TestingExtension> {
            suites.named<JvmTestSuite>("test") {
                useJUnitJupiter(junitVersion)
            }
        }
    }

    private fun VersionCatalog.version(alias: String): String =
        findVersion(alias)
            .orElseThrow {
                IllegalStateException("Missing version catalog alias '$alias'.")
            }.requiredVersion

    private companion object {
        const val JAVA_TOOLCHAIN_VERSION: Int = 21
    }
}
