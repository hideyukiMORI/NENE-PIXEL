package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.time.LocalDate
import java.time.ZoneId

public class RootQualityPlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        require(target == target.rootProject) { "nene.root-quality must be applied to the root project." }

        val validateDocumentation =
            target.tasks.register<DocumentationValidationTask>("validateDocumentation") {
                repositoryDirectory.set(target.layout.projectDirectory)
                validationDate.set(LocalDate.now(ZoneId.of("Asia/Tokyo")).toString())
            }
        val validateNoBaselines =
            target.tasks.register<NoBaselineValidationTask>("validateNoBaselines") {
                repositoryDirectory.set(target.layout.projectDirectory)
            }

        target.tasks.register("check") {
            group = "verification"
            description = "Runs every canonical local quality gate."
            dependsOn(validateDocumentation, validateNoBaselines)
        }
    }
}
