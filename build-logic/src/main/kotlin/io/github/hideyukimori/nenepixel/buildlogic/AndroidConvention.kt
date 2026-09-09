package io.github.hideyukimori.nenepixel.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureAndroidQuality(libraries: VersionCatalog) {
    configureStrictDependencyLocking()
    configureKtlint(libraries.requiredVersion("ktlint-engine"), android = true)
    configureDetekt(libraries.requiredVersion("detekt"))
    dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:${libraries.requiredVersion("junit")}")
    dependencies.add(
        "testRuntimeOnly",
        "org.junit.platform:junit-platform-launcher:${libraries.requiredVersion("junit")}",
    )
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}

internal fun Project.configureAndroidConvention(
    composeEnabled: Boolean,
    explicitApiEnabled: Boolean,
) {
    extensions.configure<CommonExtension> {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        buildFeatures.buildConfig = false
        buildFeatures.compose = composeEnabled
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        lint.abortOnError = true
        lint.checkDependencies = true
        lint.checkReleaseBuilds = true
        lint.warningsAsErrors = true
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(JAVA_TOOLCHAIN_VERSION)
        if (explicitApiEnabled) {
            explicitApi()
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

private const val COMPILE_SDK: Int = 37
private const val MIN_SDK: Int = 26
private const val JAVA_TOOLCHAIN_VERSION: Int = 21
