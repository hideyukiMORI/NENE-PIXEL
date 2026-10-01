package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster

/**
 * The accepted `IHDR` of a PNG file to import (ADR 0033).
 *
 * [PngImportStructureReader] creates it only after it has checked the sides (1 to
 * [ImportRaster.MAX_SIDE]) and the colour type and bit depth with [isSupported]; compression, filter
 * and interlace are 0 by then.
 */
internal class PngImportHeader(
    val width: Int,
    val height: Int,
    val bitDepth: Int,
    val colorType: Int,
) {
    init {
        require(width in 1..ImportRaster.MAX_SIDE && height in 1..ImportRaster.MAX_SIDE)
        require(isSupported(colorType, bitDepth))
    }

    /** The bytes of one row after its filter byte: `ceil(width * samples * bitDepth / 8)`. */
    val rowByteCount: Int =
        (width * samplesByColorType.getValue(colorType) * bitDepth + BYTE_BITS - 1) / BYTE_BITS

    /** The bytes of the whole inflated stream: `height * (1 + rowByteCount)`. */
    val inflatedByteCount: Int = height * (1 + rowByteCount)

    companion object {
        const val GREY: Int = 0
        const val TRUECOLOUR: Int = 2
        const val INDEXED: Int = 3
        const val GREY_ALPHA: Int = 4
        const val TRUECOLOUR_ALPHA: Int = 6

        private const val BYTE_BITS: Int = 8
        private val lowDepths: Set<Int> = setOf(1, 2, 4, 8)
        private val byteDepth: Set<Int> = setOf(8)
        private val depthsByColorType: Map<Int, Set<Int>> =
            mapOf(
                GREY to lowDepths,
                TRUECOLOUR to byteDepth,
                INDEXED to lowDepths,
                GREY_ALPHA to byteDepth,
                TRUECOLOUR_ALPHA to byteDepth,
            )
        private val samplesByColorType: Map<Int, Int> =
            mapOf(GREY to 1, TRUECOLOUR to 3, INDEXED to 1, GREY_ALPHA to 2, TRUECOLOUR_ALPHA to 4)

        /** True for the colour type and bit depth pairs that ADR 0033 accepts. */
        fun isSupported(
            colorType: Int,
            bitDepth: Int,
        ): Boolean = depthsByColorType[colorType]?.contains(bitDepth) == true
    }
}
