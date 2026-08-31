package io.github.hideyukimori.nenepixel.buildlogic

import java.nio.file.Files
import java.nio.file.Path

internal class ReferenceValidator(
    private val repositoryRoot: Path,
    private val documents: List<MarkdownDocument>,
) {
    fun validate(): List<DocumentationViolation> =
        buildList {
            addAll(validateLinks())
            addAll(validateNamedReferences("rule", ruleDefinitionRegex, ruleReferenceRegex))
            addAll(validateNamedReferences("work package", workPackageDefinitionRegex, workPackageReferenceRegex))
        }

    private fun validateLinks(): List<DocumentationViolation> =
        documents.flatMap { document ->
            markdownLinkRegex.findAll(document.content).mapNotNull { match ->
                val destination = match.groupValues[1].substringBefore(' ').trim('<', '>')
                validateLocalLink(document, destination)
            }
        }

    private fun validateLocalLink(
        document: MarkdownDocument,
        destination: String,
    ): DocumentationViolation? {
        if (destination.isBlank() || destination.startsWith('#') || uriSchemeRegex.containsMatchIn(destination)) {
            return null
        }
        val filePart = destination.substringBefore('#')
        val target =
            document.path.parent
                .resolve(filePart)
                .normalize()
        val isValid = target.startsWith(repositoryRoot.normalize()) && Files.exists(target)
        return if (isValid) null else DocumentationViolation(document.relativePath, "broken local link: $destination")
    }

    private fun validateNamedReferences(
        kind: String,
        definitionPattern: Regex,
        referencePattern: Regex,
    ): List<DocumentationViolation> {
        val definitions = collectMatches(definitionPattern)
        val references = collectMatches(referencePattern)
        val names = definitions.map(NamedOccurrence::name).toSet()
        val duplicates = definitions.groupBy(NamedOccurrence::name).filterValues { it.size > 1 }
        return buildList {
            references.filter { it.name !in names }.distinct().forEach {
                add(DocumentationViolation(it.file, "undefined $kind reference: ${it.name}"))
            }
            duplicates.forEach { (name, occurrences) ->
                add(DocumentationViolation(occurrences.first().file, "duplicate $kind definition: $name"))
            }
        }
    }

    private fun collectMatches(pattern: Regex): List<NamedOccurrence> =
        documents.flatMap { document ->
            pattern.findAll(document.content).map { match ->
                NamedOccurrence(match.groupValues[1], document.relativePath)
            }
        }

    private data class NamedOccurrence(
        val name: String,
        val file: String,
    )

    private companion object {
        val markdownLinkRegex = Regex("\\[[^]]*]\\(([^)]+)\\)")
        val uriSchemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
        val ruleReferenceRegex = Regex("\\b((?:ARC|CMD|KOT|QLT)-\\d{3})\\b")
        val ruleDefinitionRegex = Regex("(?m)^###\\s+((?:ARC|CMD|KOT|QLT)-\\d{3})\\b")
        val workPackageReferenceRegex = Regex("\\b(P\\d+-\\d{2})\\b")
        val workPackageDefinitionRegex = Regex("(?m)^\\|\\s*(P\\d+-\\d{2})\\s*\\|")
    }
}
