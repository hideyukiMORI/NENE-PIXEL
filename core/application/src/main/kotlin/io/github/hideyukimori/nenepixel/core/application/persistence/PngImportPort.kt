package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster

/** Picks one PNG file and reads it into a raster for import (ADR 0033). */
public fun interface PngImportPort {
    public suspend fun pick(): PngImportOutcome
}

public sealed interface PngImportOutcome {
    public data class Picked(
        public val raster: ImportRaster,
    ) : PngImportOutcome

    public data object Cancelled : PngImportOutcome

    public data class Rejected(
        public val reason: PngImportSourceRejection,
    ) : PngImportOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
    ) : PngImportOutcome
}
