package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

@DisableCachingByDefault(because = "Validation has no output artifact and must inspect the current repository tree.")
public abstract class NoBaselineValidationTask : DefaultTask() {
    @get:Internal
    public abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    public fun validateNoBaselines() {
        val root = repositoryDirectory.get().asFile.toPath()
        val baselines = Files.walk(root).use { paths -> paths.filter(::isBaseline).map(root::relativize).toList() }

        if (baselines.isNotEmpty()) {
            throw GradleException(
                baselines.joinToString(
                    prefix = "Baseline files are prohibited:\n",
                    separator = "\n",
                    transform = Path::toString,
                ),
            )
        }
    }

    private fun isBaseline(path: Path): Boolean {
        if (!Files.isRegularFile(path) || isIgnored(path)) return false
        val supportedExtension = path.extension.lowercase() in setOf("xml", "json", "sarif")
        return supportedExtension && "baseline" in path.name.lowercase()
    }

    private fun isIgnored(path: Path): Boolean =
        path.any { segment -> segment.toString() in setOf(".git", ".gradle", ".idea", ".kotlin", "build") }
}
