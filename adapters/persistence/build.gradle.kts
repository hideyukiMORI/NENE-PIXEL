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
    implementation(libs.androidx.activity)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    constraints {
        implementation(libs.androidx.lifecycle.viewmodel)
        implementation(libs.androidx.lifecycle.viewmodel.savedstate)
        implementation(libs.androidx.savedstate)
    }
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
