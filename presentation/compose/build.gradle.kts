plugins {
    alias(libs.plugins.android.library)
    id("nene.android-compose")
}

android {
    namespace = "io.github.hideyukimori.nenepixel.presentation.compose"
}

dependencies {
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
}
