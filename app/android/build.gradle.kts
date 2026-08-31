plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val applicationPackage = "io.github.hideyukimori.nenepixel"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

android {
    namespace = applicationPackage
    compileSdk = 37

    defaultConfig {
        applicationId = applicationPackage
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}
