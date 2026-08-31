package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.time.LocalDate

@DisableCachingByDefault(because = "Validation has no output artifact and must inspect the current repository tree.")
public abstract class DocumentationValidationTask : DefaultTask() {
    @get:Internal
    public abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    public abstract val validationDate: Property<String>

    @TaskAction
    public fun validateDocumentation() {
        val violations =
            DocumentationValidator(
                repositoryRoot = repositoryDirectory.get().asFile.toPath(),
                validationDate = LocalDate.parse(validationDate.get()),
            ).validate()

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "Documentation validation failed with ${violations.size} violation(s):\n",
                    separator = "\n",
                ),
            )
        }
    }
}
