package io.github.hideyukimori.nenepixel.adapters.persistence

public enum class DocumentOutputFormat {
    PROJECT,

    /** Creates a PNG export, and opens a PNG for import (ADR 0033). */
    PNG,

    PALETTE_JSON,

    /** Open only; never used to create a document (ADR 0032). */
    REFERENCE_IMAGE,
}
