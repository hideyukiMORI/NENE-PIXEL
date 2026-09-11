package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

/** Exports an immutable capture without changing project or recovery state (ADR 0019). */
public fun interface PngExportPort {
    public suspend fun export(document: DocumentState): PngExportOutcome
}
