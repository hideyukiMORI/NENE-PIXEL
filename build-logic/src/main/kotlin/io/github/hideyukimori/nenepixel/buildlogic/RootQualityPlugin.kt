package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskProvider
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
        val validateArchitecture =
            target.tasks.register<ArchitectureValidationTask>("validateArchitecture") {
                repositoryDirectory.set(target.layout.projectDirectory)
                validationDate.set(LocalDate.now(ZoneId.of("Asia/Tokyo")).toString())
            }

        collectArchitectureInputs(target, validateArchitecture)

        target.tasks.register("check") {
            group = "verification"
            description = "Runs every canonical local quality gate."
            dependsOn(validateArchitecture, validateDocumentation, validateNoBaselines)
        }
    }

    private fun collectArchitectureInputs(
        target: Project,
        validateArchitecture: TaskProvider<ArchitectureValidationTask>,
    ) {
        target.allprojects(
            Action<Project> {
                collectProjectInputs(this, validateArchitecture)
            },
        )
    }

    private fun collectProjectInputs(
        inspectedProject: Project,
        validateArchitecture: TaskProvider<ArchitectureValidationTask>,
    ) {
        validateArchitecture.configure {
            modulePaths.add(inspectedProject.path)
        }
        inspectedProject.configurations.configureEach {
            val configurationName = name
            dependencies.configureEach {
                recordDependency(inspectedProject.path, configurationName, this, validateArchitecture)
            }
        }
    }

    private fun recordDependency(
        projectPath: String,
        configurationName: String,
        dependency: Dependency,
        validateArchitecture: TaskProvider<ArchitectureValidationTask>,
    ) {
        validateArchitecture.configure {
            when (dependency) {
                is ProjectDependency -> {
                    moduleDependencies.add("$projectPath|$configurationName|${dependency.path}")
                }

                else -> {
                    dependency.group?.let { group ->
                        externalDependencies.add("$projectPath|$configurationName|$group|${dependency.name}")
                    }
                }
            }
        }
    }
}
