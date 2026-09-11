package io.github.hideyukimori.nenepixel.adapters.persistence

/** One fresh SAF destination request for the two supported outputs (ADR 0019). */
public data class DocumentCreationRequest(
    public val suggestedName: String,
    public val format: DocumentOutputFormat,
)
