package io.github.hideyukimori.nenepixel.buildlogic

internal data class DocumentationViolation(
    val file: String,
    val message: String,
) : Comparable<DocumentationViolation> {
    override fun compareTo(other: DocumentationViolation): Int =
        compareValuesBy(this, other, DocumentationViolation::file, DocumentationViolation::message)

    override fun toString(): String = "$file: $message"
}
