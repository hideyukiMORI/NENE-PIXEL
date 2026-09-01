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
    }
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(project(":presentation:compose"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
