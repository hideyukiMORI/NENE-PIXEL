package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.time.LocalDate

@DisableCachingByDefault(because = "Validation has no output artifact and inspects the current repository tree.")
public abstract class ArchitectureValidationTask : DefaultTask() {
    @get:Internal
    public abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    public abstract val validationDate: Property<String>

    @get:Input
    public abstract val modulePaths: ListProperty<String>

    @get:Input
    public abstract val moduleDependencies: ListProperty<String>

    @get:Input
    public abstract val externalDependencies: ListProperty<String>

    @TaskAction
    public fun validateArchitecture() {
        val violations =
            ModuleArchitectureValidator(
                modulePaths = modulePaths.get().toSet(),
                moduleDependencies = moduleDependencies.get().map(::parseModuleDependency),
                externalDependencies = externalDependencies.get().map(::parseExternalDependency),
            ).validate() +
                SuppressionValidator(
                    repositoryRoot = repositoryDirectory.get().asFile.toPath(),
                    validationDate = LocalDate.parse(validationDate.get()),
                ).validate()

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.sorted().joinToString(
                    prefix = "Architecture validation failed with ${violations.size} violation(s):\n",
                    separator = "\n",
                ),
            )
        }
    }

    private fun parseModuleDependency(record: String): DeclaredModuleDependency {
        val parts = record.split('|', limit = MODULE_DEPENDENCY_FIELD_COUNT)
        require(parts.size == MODULE_DEPENDENCY_FIELD_COUNT) { "Invalid module dependency record: $record" }
        val (source, configuration, target) = parts
        return DeclaredModuleDependency(source, configuration, target)
    }

    private fun parseExternalDependency(record: String): DeclaredExternalDependency {
        val parts = record.split('|', limit = EXTERNAL_DEPENDENCY_FIELD_COUNT)
        require(parts.size == EXTERNAL_DEPENDENCY_FIELD_COUNT) { "Invalid external dependency record: $record" }
        val fields = parts.iterator()
        return DeclaredExternalDependency(fields.next(), fields.next(), fields.next(), fields.next())
    }

    private companion object {
        const val MODULE_DEPENDENCY_FIELD_COUNT = 3
        const val EXTERNAL_DEPENDENCY_FIELD_COUNT = 4
    }
}
