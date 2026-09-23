package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/** Imports one palette definition through the document output lease (ADR 0019). */
public fun interface PaletteJsonImportPort {
    public suspend fun import(): PaletteJsonImportOutcome
}

public sealed interface PaletteJsonImportOutcome {
    public data class Imported(
        public val definition: PaletteDefinition,
    ) : PaletteJsonImportOutcome

    public data object Cancelled : PaletteJsonImportOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
    ) : PaletteJsonImportOutcome
}
