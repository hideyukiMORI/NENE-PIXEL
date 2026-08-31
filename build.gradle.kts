import org.gradle.api.artifacts.dsl.LockMode

plugins {
    id("nene.root-quality")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
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

tasks.named("check") {
    dependsOn("ktlintCheck")
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
    dependsOn(":app:android:check")
}
