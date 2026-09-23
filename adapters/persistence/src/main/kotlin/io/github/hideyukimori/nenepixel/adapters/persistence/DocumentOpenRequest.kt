package io.github.hideyukimori.nenepixel.adapters.persistence

/** One SAF source request; the format selects the picker MIME type (ADR 0019). */
public data class DocumentOpenRequest(
    public val format: DocumentOutputFormat,
)
