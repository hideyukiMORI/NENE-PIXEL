package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
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

public class KotlinLibraryPlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("org.jetbrains.kotlin.jvm")
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        val libraries = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        configureToolchains(target)
        target.configureStrictDependencyLocking()
        configureKotlin(target)
        target.configureKtlint(libraries.requiredVersion("ktlint-engine"), android = false)
        target.configureDetekt(libraries.requiredVersion("detekt"))
        configureTests(target, libraries.requiredVersion("junit"))
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

    private companion object {
        const val JAVA_TOOLCHAIN_VERSION: Int = 21
    }
}
