import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.library)
    id("nene.android-compose")
}

android {
    namespace = "io.github.hideyukimori.nenepixel.presentation.compose"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val p2ViewportInteractionMeasurementClass =
    "io.github.hideyukimori.nenepixel.presentation.compose.editor.P2ViewportInteractionMeasurementTest"
val p2RenderProjectionMeasurementClass =
    "io.github.hideyukimori.nenepixel.presentation.compose.editor.P2RenderProjectionMeasurementTest"

afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest")
    tasks.register<Test>("measureP2ViewportInteraction") {
        group = "verification"
        description = "Measures the P2 canonical viewport controller transform path."
        dependsOn(tasks.named("compileDebugUnitTestKotlin"))
        testClassesDirs = debugUnitTest.get().testClassesDirs
        classpath = debugUnitTest.get().classpath
        filter.includeTestsMatching(p2ViewportInteractionMeasurementClass)
        useJUnitPlatform()
        maxParallelForks = 1
        jvmArgs("-Xms512m", "-Xmx512m")
        systemProperty(
            "nene.p2.viewport.measurement.outputDirectory",
            rootProject.layout.buildDirectory
                .dir("reports/p2/viewport")
                .get()
                .asFile.absolutePath,
        )
        outputs.upToDateWhen { false }
    }
    tasks.register<Test>("measureP2RenderProjection") {
        group = "verification"
        description = "Measures the P2 current host PixelSnapshot render-projection path."
        dependsOn(tasks.named("compileDebugUnitTestKotlin"))
        testClassesDirs = debugUnitTest.get().testClassesDirs
        classpath = debugUnitTest.get().classpath
        filter.includeTestsMatching(p2RenderProjectionMeasurementClass)
        useJUnitPlatform()
        maxParallelForks = 1
        jvmArgs("-Xms512m", "-Xmx512m")
        systemProperty(
            "nene.p2.projection.measurement.outputDirectory",
            rootProject.layout.buildDirectory
                .dir("reports/p2/representation-limits")
                .get()
                .asFile.absolutePath,
        )
        outputs.upToDateWhen { false }
    }
}
