import org.gradle.api.artifacts.dsl.LockMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "io.github.hideyukimori.nenepixel.buildlogic"

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget = JvmTarget.JVM_17
    }
}

ktlint {
    version = libs.versions.ktlint.engine
    ignoreFailures = false
    outputToConsole = true
    relative = true
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = false
    failOnSeverity = dev.detekt.gradle.extensions.FailOnSeverity.Warning
    basePath = rootProject.projectDir.parentFile
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.get())
        }
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("kotlinLibrary") {
            id = "nene.kotlin-library"
            implementationClass = "io.github.hideyukimori.nenepixel.buildlogic.KotlinLibraryPlugin"
        }
        create("androidCompose") {
            id = "nene.android-compose"
            implementationClass = "io.github.hideyukimori.nenepixel.buildlogic.AndroidComposePlugin"
        }
        create("rootQuality") {
            id = "nene.root-quality"
            implementationClass = "io.github.hideyukimori.nenepixel.buildlogic.RootQualityPlugin"
        }
    }
}
