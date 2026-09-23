package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/** Exports an immutable palette definition through the document output lease (ADR 0019). */
public fun interface PaletteJsonExportPort {
    public suspend fun export(definition: PaletteDefinition): PaletteJsonExportOutcome
}
