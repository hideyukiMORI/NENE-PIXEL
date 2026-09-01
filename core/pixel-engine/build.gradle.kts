import org.gradle.api.tasks.testing.Test

plugins {
    id("nene.kotlin-library")
}

group = "io.github.hideyukimori.nenepixel.core"

dependencies {
    implementation(project(":core:domain"))
}

val pixelEngineTest = tasks.named<Test>("test")

tasks.register<Test>("measureP2RepresentationCandidates") {
    group = "verification"
    description = "Runs test-only P2 pixel representation candidates inside the mutation enclave."
    dependsOn(tasks.named("compileTestKotlin"))
    testClassesDirs = pixelEngineTest.get().testClassesDirs
    classpath = pixelEngineTest.get().classpath
    filter {
        includeTestsMatching(
            "io.github.hideyukimori.nenepixel.core.pixelengine.measurement.P2CandidateMeasurementTest",
        )
    }
    useJUnitPlatform()
    maxParallelForks = 1
    jvmArgs("-Xms512m", "-Xmx512m")
    systemProperty(
        "nene.p2.representation.measurement.outputDirectory",
        rootProject.layout.buildDirectory
            .dir("reports/p2/representation-limits")
            .get()
            .asFile
            .absolutePath,
    )
    outputs.upToDateWhen { false }
}
