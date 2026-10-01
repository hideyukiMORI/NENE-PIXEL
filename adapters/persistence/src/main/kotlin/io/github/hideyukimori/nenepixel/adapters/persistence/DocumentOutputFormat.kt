package io.github.hideyukimori.nenepixel.adapters.persistence

public enum class DocumentOutputFormat {
    PROJECT,
    PNG,
    PALETTE_JSON,

    /** Open only; never used to create a document (ADR 0032). */
    REFERENCE_IMAGE,
}
