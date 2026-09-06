package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Validation has no output artifact and must inspect the current repository tree.")
public abstract class BaselineProfileValidationTask : DefaultTask() {
    @get:Internal
    public abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    public fun validateBaselineProfile() {
        val violations = BaselineProfileArtifactValidator(repositoryDirectory.get().asFile.toPath()).validate()
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(prefix = "Baseline Profile validation failed:\n", separator = "\n"),
            )
        }
    }
}
