plugins {
    alias(libs.plugins.android.library)
    id("nene.android-library")
}

group = "io.github.hideyukimori.nenepixel.adapters"

android {
    namespace = "io.github.hideyukimori.nenepixel.adapters.persistence"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(project(":core:project-format"))
    implementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
