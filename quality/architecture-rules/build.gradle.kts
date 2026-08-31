plugins {
    id("nene.kotlin-library")
}

group = "io.github.hideyukimori.nenepixel.quality"

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
}
