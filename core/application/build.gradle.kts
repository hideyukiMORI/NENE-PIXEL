import org.gradle.api.tasks.testing.Test

plugins {
    id("nene.kotlin-library")
}

group = "io.github.hideyukimori.nenepixel.core"

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:pixel-engine"))
}

val applicationTest = tasks.named<Test>("test")

tasks.register<Test>("measureP2RepresentationLimits") {
    group = "verification"
    description = "Runs the fixed-heap P2 pixel representation and limit measurement harness."
    dependsOn(tasks.named("compileTestKotlin"))
    testClassesDirs = applicationTest.get().testClassesDirs
    classpath = applicationTest.get().classpath
    filter {
        includeTestsMatching(
            "io.github.hideyukimori.nenepixel.core.application.document.history." +
                "P2RepresentationLimitMeasurementTest",
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
