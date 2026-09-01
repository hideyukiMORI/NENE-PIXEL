import dev.detekt.gradle.Detekt
import org.gradle.api.artifacts.dsl.LockMode

plugins {
    id("nene.root-quality")
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
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
    dependsOn(":core:application:check")
    dependsOn(":core:domain:check")
    dependsOn(":core:pixel-engine:check")
    dependsOn(":quality:architecture-rules:check")
}
