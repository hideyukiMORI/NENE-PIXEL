package io.github.hideyukimori.nenepixel.adapters.persistence

internal sealed interface PngImportStructureResult {
    class Parsed(
        val structure: PngImportStructure,
    ) : PngImportStructureResult

    /** A side of the header is above `ImportRaster.MAX_SIDE`. */
    data object TooManyPixels : PngImportStructureResult

    /** Malformed bytes or content outside ADR 0033. */
    data object Unsupported : PngImportStructureResult
}
