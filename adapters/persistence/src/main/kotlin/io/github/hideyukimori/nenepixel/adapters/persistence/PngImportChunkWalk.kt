package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * Applies the chunk-order rules of ADR 0033 to the chunks after `IHDR`, one at a time, and collects
 * `PLTE`, `tRNS` and where the consecutive `IDAT` chunks lie. One walk reads one file; it is never
 * shared.
 */
internal class PngImportChunkWalk(
    private val encoded: ByteArray,
    private val header: PngImportHeader,
) {
    private var paletteSeen: Boolean = false
    private var palette: ByteArray? = null
    private var transparency: ByteArray? = null
    private var phase: DataPhase = DataPhase.BEFORE_DATA
    private var dataStart: Int = 0
    private var dataEnd: Int = 0

    /** True when [chunk], which is not `IEND`, is accepted at this position. */
    fun accept(chunk: PngImportChunk): Boolean {
        val accepted =
            when (chunk.type) {
                PngImportChunk.PALETTE -> {
                    acceptPalette(chunk)
                }

                PngImportChunk.TRANSPARENCY -> {
                    acceptTransparency(chunk)
                }

                PngImportChunk.DATA -> {
                    acceptData(chunk)
                }

                else -> {
                    !chunk.isCritical
                }
            }
        if (chunk.type != PngImportChunk.DATA && phase == DataPhase.IN_DATA) {
            phase = DataPhase.AFTER_DATA
        }
        return accepted
    }

    /** The structure that ends with [end], an `IEND` chunk. */
    fun finish(end: PngImportChunk): PngImportStructureResult =
        if (end.length == 0 && phase != DataPhase.BEFORE_DATA) {
            PngImportStructureResult.Parsed(
                PngImportStructure(header, palette, transparency, PngImportDataSpan(dataStart, dataEnd)),
            )
        } else {
            PngImportStructureResult.Unsupported
        }

    private fun acceptPalette(chunk: PngImportChunk): Boolean {
        val entries = chunk.length / PALETTE_ENTRY_BYTES
        val accepted =
            phase == DataPhase.BEFORE_DATA &&
                !paletteSeen &&
                chunk.length % PALETTE_ENTRY_BYTES == 0 &&
                entries in 1..maxPaletteEntries()
        paletteSeen = true
        if (accepted && header.colorType == PngImportColorType.INDEXED) {
            palette = chunk.dataOf(encoded)
        }
        return accepted
    }

    /** The most `PLTE` entries for the colour type; 0 where `PLTE` is not allowed. */
    private fun maxPaletteEntries(): Int =
        when (header.colorType) {
            PngImportColorType.INDEXED -> {
                minOf(MAX_PALETTE_ENTRIES, 1 shl header.bitDepth)
            }

            PngImportColorType.TRUECOLOUR, PngImportColorType.TRUECOLOUR_ALPHA -> {
                MAX_PALETTE_ENTRIES
            }

            PngImportColorType.GREY, PngImportColorType.GREY_ALPHA -> {
                0
            }
        }

    private fun acceptTransparency(chunk: PngImportChunk): Boolean {
        val accepted = phase == DataPhase.BEFORE_DATA && transparency == null && transparencyFits(chunk.length)
        if (accepted) {
            transparency = chunk.dataOf(encoded)
        }
        return accepted
    }

    /** True when a `tRNS` of [length] bytes suits the colour type and, for type 3, the palette before it. */
    private fun transparencyFits(length: Int): Boolean =
        when (header.colorType) {
            PngImportColorType.INDEXED -> {
                palette.let { it != null && length <= it.size / PALETTE_ENTRY_BYTES }
            }

            PngImportColorType.GREY -> {
                length == GREY_TRANSPARENCY_BYTES
            }

            PngImportColorType.TRUECOLOUR -> {
                length == TRUECOLOUR_TRANSPARENCY_BYTES
            }

            PngImportColorType.GREY_ALPHA, PngImportColorType.TRUECOLOUR_ALPHA -> {
                false
            }
        }

    private fun acceptData(chunk: PngImportChunk): Boolean {
        val accepted =
            phase != DataPhase.AFTER_DATA &&
                (header.colorType != PngImportColorType.INDEXED || palette != null)
        if (accepted) {
            if (phase == DataPhase.BEFORE_DATA) {
                dataStart = chunk.dataOffset - LENGTH_AND_TYPE_BYTES
            }
            dataEnd = chunk.end
            phase = DataPhase.IN_DATA
        }
        return accepted
    }

    private companion object {
        const val PALETTE_ENTRY_BYTES: Int = 3
        const val MAX_PALETTE_ENTRIES: Int = 256
        const val GREY_TRANSPARENCY_BYTES: Int = 2
        const val TRUECOLOUR_TRANSPARENCY_BYTES: Int = 6
        const val LENGTH_AND_TYPE_BYTES: Int = 8
    }
}

/** Where the walk is relative to the consecutive `IDAT` chunks. */
private enum class DataPhase {
    BEFORE_DATA,
    IN_DATA,
    AFTER_DATA,
}
