package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster

/**
 * The accepted `IHDR` of a PNG file to import (ADR 0033).
 *
 * [PngImportStructureReader] creates it only after it has checked the sides (1 to
 * [ImportRaster.MAX_SIDE]) and that [bitDepth] is one of [PngImportColorType.bitDepths] of
 * [colorType]; compression, filter and interlace are 0 by then.
 */
internal class PngImportHeader(
    val width: Int,
    val height: Int,
    val bitDepth: Int,
    val colorType: PngImportColorType,
) {
    init {
        require(width in 1..ImportRaster.MAX_SIDE && height in 1..ImportRaster.MAX_SIDE)
        require(bitDepth in colorType.bitDepths)
    }

    /** The bytes of one row after its filter byte: `ceil(width * samples * bitDepth / 8)`. */
    val rowByteCount: Int = (width * colorType.samplesPerPixel * bitDepth + BYTE_BITS - 1) / BYTE_BITS

    /** The bytes of the whole inflated stream: `height * (1 + rowByteCount)`. */
    val inflatedByteCount: Int = height * (1 + rowByteCount)

    private companion object {
        const val BYTE_BITS: Int = 8
    }
}
