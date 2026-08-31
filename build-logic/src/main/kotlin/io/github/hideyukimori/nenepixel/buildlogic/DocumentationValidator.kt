package io.github.hideyukimori.nenepixel.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.name
import kotlin.io.path.readText

internal class DocumentationValidator(
    private val repositoryRoot: Path,
    private val validationDate: LocalDate,
) {
    fun validate(): List<DocumentationViolation> {
        val documents = loadDocuments()
        return buildList {
            addAll(ReferenceValidator(repositoryRoot, documents).validate())
            addAll(DecisionRecordValidator(documents, validationDate).validate())
        }.sorted()
    }

    private fun loadDocuments(): List<MarkdownDocument> =
        Files.walk(repositoryRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.name.endsWith(".md") }
                .filter { path -> path.none { it.toString() in ignoredDirectories } }
                .map { path -> MarkdownDocument(path, relative(path), path.readText()) }
                .sorted(compareBy(MarkdownDocument::relativePath))
                .toList()
        }

    private fun relative(path: Path): String = repositoryRoot.relativize(path).toString().replace('\\', '/')

    private companion object {
        val ignoredDirectories = setOf(".git", ".gradle", ".idea", ".kotlin", "build")
    }
}
