import dev.detekt.gradle.Detekt
import org.gradle.api.artifacts.dsl.LockMode

plugins {
    id("nene.root-quality")
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint)
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

ktlint {
    version = libs.versions.ktlint.engine
    ignoreFailures = false
    outputToConsole = true
    relative = true
}

subprojects {
    plugins.withId("dev.detekt") {
        if (path != ":quality:architecture-rules") {
            dependencies.add(
                "detektPlugins",
                dependencies.project(":quality:architecture-rules"),
            )
            tasks.withType<Detekt>().configureEach {
                dependsOn(":quality:architecture-rules:jar")
            }
        }
    }
}

tasks.named("check") {
    dependsOn("ktlintCheck")
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
    dependsOn(":app:android:check")
    dependsOn(":presentation:compose:check")
    dependsOn(":core:application:check")
    dependsOn(":core:domain:check")
    dependsOn(":core:pixel-engine:check")
    dependsOn(":quality:architecture-rules:check")
}

tasks.register("measureP2ViewportInteraction") {
    group = "verification"
    description = "Measures the P2 canonical viewport-to-controller interaction path."
    dependsOn(":presentation:compose:measureP2ViewportInteraction")
}

tasks.register("measureP2RepresentationLimits") {
    group = "verification"
    description = "Runs the P2 pixel representation and limit measurement harness."
    dependsOn(
        ":core:application:measureP2RepresentationLimits",
        ":core:pixel-engine:measureP2RepresentationCandidates",
    )
}
