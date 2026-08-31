package io.github.hideyukimori.nenepixel.buildlogic

import java.time.LocalDate
import kotlin.io.path.name

internal class DecisionRecordValidator(
    private val documents: List<MarkdownDocument>,
    private val validationDate: LocalDate,
) {
    fun validate(): List<DocumentationViolation> = validateAdrs() + validateWaivers()

    private fun validateAdrs(): List<DocumentationViolation> {
        val index = documentAt("docs/adr/README.md") ?: return emptyList()
        return documents
            .filter { it.relativePath.matches(adrFileRegex) && !it.relativePath.endsWith("0000-template.md") }
            .flatMap { document -> validateAdr(document, index) }
    }

    private fun validateAdr(
        document: MarkdownDocument,
        index: MarkdownDocument,
    ): List<DocumentationViolation> {
        val number = document.path.name.substringBefore('-')
        return buildList {
            requirePattern(document, Regex("(?m)^# ADR $number: .+$"), "ADR title", this)
            requirePattern(document, adrStatusRegex, "ADR status", this)
            requirePattern(document, dateRegex, "ADR date", this)
            requirePattern(document, issueRegex, "ADR Issue", this)
            requirePattern(document, affectedRulesRegex, "ADR affected rules", this)
            requireSections(document, adrSections, this)
            if ("(${document.path.name})" !in index.content) {
                add(DocumentationViolation(index.relativePath, "ADR missing from index: ${document.path.name}"))
            }
        }
    }

    private fun validateWaivers(): List<DocumentationViolation> {
        val index = documentAt("docs/waivers/README.md") ?: return emptyList()
        return documents
            .filter { it.relativePath.matches(waiverFileRegex) && !it.relativePath.endsWith("0000-template.md") }
            .flatMap { document -> validateWaiver(document, index) }
    }

    private fun validateWaiver(
        document: MarkdownDocument,
        index: MarkdownDocument,
    ): List<DocumentationViolation> =
        buildList {
            val number =
                document.path.name
                    .substringAfter("WVR-")
                    .substringBefore('-')
            requirePattern(document, Regex("(?m)^# WVR-$number: .+$"), "waiver title", this)
            waiverMetadata.forEach { (label, pattern) ->
                requirePattern(document, pattern, "waiver $label", this)
            }
            requireSections(document, waiverSections, this)
            validateWaiverExpiry(document)?.let(::add)
            if ("(${document.path.name})" !in index.content) {
                add(DocumentationViolation(index.relativePath, "waiver missing from index: ${document.path.name}"))
            }
        }

    private fun validateWaiverExpiry(document: MarkdownDocument): DocumentationViolation? {
        val status = waiverStatusRegex.find(document.content)?.groupValues?.get(1)
        val expiry = waiverExpiryRegex.find(document.content)?.groupValues?.get(1)
        if (status == null || expiry == null) return null
        val expiryDate = runCatching { LocalDate.parse(expiry) }.getOrNull()
        return when {
            expiryDate == null -> {
                DocumentationViolation(document.relativePath, "invalid waiver expiry date: $expiry")
            }

            status == "active" && !expiryDate.isAfter(validationDate) -> {
                DocumentationViolation(document.relativePath, "active waiver expired on $expiry")
            }

            else -> {
                null
            }
        }
    }

    private fun requirePattern(
        document: MarkdownDocument,
        pattern: Regex,
        description: String,
        violations: MutableList<DocumentationViolation>,
    ) {
        if (!pattern.containsMatchIn(document.content)) {
            violations.add(DocumentationViolation(document.relativePath, "missing or invalid $description"))
        }
    }

    private fun requireSections(
        document: MarkdownDocument,
        sections: List<String>,
        violations: MutableList<DocumentationViolation>,
    ) {
        sections.filter { section -> "\n## $section\n" !in document.content }.forEach { section ->
            violations.add(DocumentationViolation(document.relativePath, "missing section: $section"))
        }
    }

    private fun documentAt(relativePath: String): MarkdownDocument? = documents.find { it.relativePath == relativePath }

    private companion object {
        val adrFileRegex = Regex("docs/adr/\\d{4}-[^/]+\\.md")
        val waiverFileRegex = Regex("docs/waivers/WVR-\\d{4}-[^/]+\\.md")
        val adrStatusRegex = Regex("(?m)^- Status: (?:proposed|accepted|rejected|superseded)$")
        val dateRegex = Regex("(?m)^- Date: \\d{4}-\\d{2}-\\d{2}$")
        val issueRegex = Regex("(?m)^- Issue: #\\d+$")
        val affectedRulesRegex = Regex("(?m)^- Affected rules: .*(?:ARC|CMD|KOT|QLT)-\\d{3}.*$")
        val waiverStatusRegex = Regex("(?m)^- Status: (proposed|active|expired|removed)$")
        val waiverExpiryRegex = Regex("(?m)^- Expires: (\\S+)$")
        val waiverMetadata =
            listOf(
                "status" to waiverStatusRegex,
                "rule" to Regex("(?m)^- Rule: `(?:ARC|CMD|KOT|QLT)-\\d{3}`$"),
                "Issue" to issueRegex,
                "owner" to Regex("(?m)^- Owner: \\S.+$"),
                "creation date" to Regex("(?m)^- Created: \\d{4}-\\d{2}-\\d{2}$"),
                "expiry" to waiverExpiryRegex,
                "scope" to Regex("(?m)^- Scope: `[^`#]+#[^`#]+`$"),
            )
        val adrSections =
            listOf(
                "Context",
                "Decision",
                "Rejected alternatives",
                "Consequences",
                "Enforcement impact",
                "Migration and rollback",
                "Related",
            )
        val waiverSections =
            listOf(
                "Exact scope",
                "Reason",
                "Risk and containment",
                "Removal condition",
                "Rejected alternatives",
                "References",
            )
    }
}
