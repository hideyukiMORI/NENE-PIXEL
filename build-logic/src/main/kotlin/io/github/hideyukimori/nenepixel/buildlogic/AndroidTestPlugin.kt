package io.github.hideyukimori.nenepixel.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

public class AndroidTestPlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        val libraries = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        target.configureStrictDependencyLocking()
        target.configureKtlint(libraries.requiredVersion("ktlint-engine"), android = true)
        target.configureDetekt(libraries.requiredVersion("detekt"))

        target.pluginManager.withPlugin("com.android.test") {
            configureAndroid(target)
            configureKotlin(target)
            target.tasks.named("check") {
                dependsOn(target.tasks.withType<KotlinCompile>())
            }
        }
    }

    private fun configureAndroid(target: Project) {
        target.extensions.configure<CommonExtension> {
            compileSdk = COMPILE_SDK
            defaultConfig.minSdk = MIN_SDK
            buildFeatures.buildConfig = false
            compileOptions.sourceCompatibility = JavaVersion.VERSION_17
            compileOptions.targetCompatibility = JavaVersion.VERSION_17
        }
    }

    private fun configureKotlin(target: Project) {
        target.extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(JAVA_TOOLCHAIN_VERSION)
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
