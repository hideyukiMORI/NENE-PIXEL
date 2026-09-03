plugins {
    id("nene.kotlin-library")
}

group = "io.github.hideyukimori.nenepixel.core"

dependencies {
    implementation(project(":core:domain"))
}
