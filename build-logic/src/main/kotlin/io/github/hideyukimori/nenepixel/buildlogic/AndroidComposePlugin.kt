package io.github.hideyukimori.nenepixel.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

public class AndroidComposePlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("org.jetbrains.kotlin.plugin.compose")
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        val libraries = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        target.configureStrictDependencyLocking()
        target.configureKtlint(libraries.requiredVersion("ktlint-engine"), android = true)
        target.configureDetekt(libraries.requiredVersion("detekt"))
        target.configureJUnit(libraries.requiredVersion("junit"))
        target.tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        target.pluginManager.withPlugin("com.android.application") {
            configureAndroid(target)
            configureKotlin(target, explicitApi = false)
        }
        target.pluginManager.withPlugin("com.android.library") {
            configureAndroid(target)
            configureKotlin(target, explicitApi = true)
        }
    }

    private fun Project.configureJUnit(version: String) {
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:$version")
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:$version")
    }

    private fun configureAndroid(target: Project) {
        target.extensions.configure<CommonExtension> {
            compileSdk = COMPILE_SDK
            defaultConfig.minSdk = MIN_SDK
            buildFeatures.buildConfig = false
            buildFeatures.compose = true
            compileOptions.sourceCompatibility = JavaVersion.VERSION_17
            compileOptions.targetCompatibility = JavaVersion.VERSION_17
            lint.abortOnError = true
            lint.checkDependencies = true
            lint.checkReleaseBuilds = true
            lint.warningsAsErrors = true
        }
    }

    private fun configureKotlin(
        target: Project,
        explicitApi: Boolean,
    ) {
        target.extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(JAVA_TOOLCHAIN_VERSION)
            if (explicitApi) {
                explicitApi()
            }
        }
        target.tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                allWarningsAsErrors.set(true)
                freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    private companion object {
        const val COMPILE_SDK: Int = 37
        const val MIN_SDK: Int = 26
        const val JAVA_TOOLCHAIN_VERSION: Int = 21
    }
}
