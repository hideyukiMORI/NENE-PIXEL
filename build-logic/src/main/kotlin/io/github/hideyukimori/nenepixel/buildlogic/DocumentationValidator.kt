package io.github.hideyukimori.nenepixel.buildlogic

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
        RepositoryFileTraversal
            .regularFiles(repositoryRoot)
            .asSequence()
            .filter { path -> path.name.endsWith(".md") }
            .map { path -> MarkdownDocument(path, relative(path), path.readText()) }
            .sortedBy(MarkdownDocument::relativePath)
            .toList()

    private fun relative(path: Path): String = repositoryRoot.relativize(path).toString().replace('\\', '/')
}
