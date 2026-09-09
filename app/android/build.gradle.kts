plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    id("nene.android-compose")
}

val applicationPackage = "io.github.hideyukimori.nenepixel"

android {
    namespace = applicationPackage

    defaultConfig {
        applicationId = applicationPackage
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

pluginManager.withPlugin("androidx.baselineprofile.apptarget") {
    androidComponents.finalizeDsl { extension ->
        val releaseOptimization =
            extension.buildTypes
                .named("release")
                .get()
                .optimization
        val benchmarkOptimization =
            extension.buildTypes
                .named("benchmarkRelease")
                .get()
                .optimization
        val producerOptimization =
            extension.buildTypes
                .named("nonMinifiedRelease")
                .get()
                .optimization

        check(releaseOptimization !== benchmarkOptimization)
        check(releaseOptimization !== producerOptimization)
        check(benchmarkOptimization !== producerOptimization)

        releaseOptimization.enable = true
        benchmarkOptimization.enable = true
        producerOptimization.enable = false

        check(releaseOptimization.enable)
        check(benchmarkOptimization.enable)
        check(!producerOptimization.enable)
    }
}

baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(project(":quality:baseline-profile"))
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(project(":adapters:persistence"))
    implementation(project(":presentation:compose"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
