plugins {
    alias(libs.plugins.android.application)
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

dependencies {
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(project(":presentation:compose"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
