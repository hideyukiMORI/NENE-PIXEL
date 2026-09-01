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

val m1InteractionMeasurementClass =
    "io.github.hideyukimori.nenepixel.presentation.compose.editor.M1InteractionMeasurementTest"
val m1CoreMeasurementClass =
    "io.github.hideyukimori.nenepixel.core.application.document.command.M1CoreMeasurementTest"

afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest")
    val coreUnitTest = project(":core:application").tasks.named<Test>("test")
    tasks.register<Test>("measureM1VerticalSlice") {
        group = "verification"
        description = "Measures the M1 canonical core and translated controller paths."
        dependsOn(tasks.named("compileDebugUnitTestKotlin"))
        dependsOn(":core:application:testClasses")
        testClassesDirs = debugUnitTest.get().testClassesDirs + coreUnitTest.get().testClassesDirs
        classpath = debugUnitTest.get().classpath + coreUnitTest.get().classpath
        filter.includeTestsMatching(m1InteractionMeasurementClass)
        filter.includeTestsMatching(m1CoreMeasurementClass)
        useJUnitPlatform()
        maxParallelForks = 1
        jvmArgs("-Xms512m", "-Xmx512m")
        systemProperty(
            "nene.m1.measurement.outputDirectory",
            rootProject.layout.buildDirectory
                .dir("reports/m1")
                .get()
                .asFile.absolutePath,
        )
        outputs.upToDateWhen { false }
    }
}
