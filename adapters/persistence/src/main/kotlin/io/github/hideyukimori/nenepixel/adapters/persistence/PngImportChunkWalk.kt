package io.github.hideyukimori.nenepixel.adapters.persistence

import java.io.ByteArrayOutputStream

/**
 * Applies the chunk-order rules of ADR 0033 to the chunks after `IHDR`, one at a time, and collects
 * `PLTE`, `tRNS` and the joined `IDAT` data. One walk reads one file; it is never shared.
 */
internal class PngImportChunkWalk(
    private val encoded: ByteArray,
    private val header: PngImportHeader,
) {
    private var paletteSeen: Boolean = false
    private var palette: ByteArray? = null
    private var transparency: ByteArray? = null
    private var dataSeen: Boolean = false
    private var dataOpen: Boolean = false
    private val compressed: ByteArrayOutputStream = ByteArrayOutputStream()

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
        dataOpen = chunk.type == PngImportChunk.DATA
        return accepted
    }

    /** The structure that ends with [end], an `IEND` chunk. */
    fun finish(end: PngImportChunk): PngImportStructureResult =
        if (end.length == 0 && dataSeen) {
            PngImportStructureResult.Parsed(
                PngImportStructure(header, palette, transparency, compressed.toByteArray()),
            )
        } else {
            PngImportStructureResult.Unsupported
        }

    private fun acceptPalette(chunk: PngImportChunk): Boolean {
        val entries = chunk.length / PALETTE_ENTRY_BYTES
        val accepted =
            !dataSeen &&
                !paletteSeen &&
                chunk.length % PALETTE_ENTRY_BYTES == 0 &&
                entries in 1..maxPaletteEntries()
        paletteSeen = true
        if (accepted && header.colorType == PngImportHeader.INDEXED) {
            palette = chunk.dataOf(encoded)
        }
        return accepted
    }

    /** The most `PLTE` entries for the colour type; 0 where `PLTE` is not allowed. */
    private fun maxPaletteEntries(): Int =
        when (header.colorType) {
            PngImportHeader.INDEXED -> {
                minOf(MAX_PALETTE_ENTRIES, 1 shl header.bitDepth)
            }

            PngImportHeader.TRUECOLOUR, PngImportHeader.TRUECOLOUR_ALPHA -> {
                MAX_PALETTE_ENTRIES
            }

            else -> {
                0
            }
        }

    private fun acceptTransparency(chunk: PngImportChunk): Boolean {
        val accepted = !dataSeen && transparency == null && transparencyFits(chunk.length)
        if (accepted) {
            transparency = chunk.dataOf(encoded)
        }
        return accepted
    }

    /** True when a `tRNS` of [length] bytes suits the colour type and, for type 3, the palette before it. */
    private fun transparencyFits(length: Int): Boolean =
        when (header.colorType) {
            PngImportHeader.INDEXED -> {
                palette.let { it != null && length <= it.size / PALETTE_ENTRY_BYTES }
            }

            PngImportHeader.GREY -> {
                length == GREY_TRANSPARENCY_BYTES
            }

            PngImportHeader.TRUECOLOUR -> {
                length == TRUECOLOUR_TRANSPARENCY_BYTES
            }

            else -> {
                false
            }
        }

    private fun acceptData(chunk: PngImportChunk): Boolean {
        val accepted =
            (!dataSeen || dataOpen) &&
                (header.colorType != PngImportHeader.INDEXED || palette != null)
        if (accepted) {
            compressed.write(encoded, chunk.dataOffset, chunk.length)
            dataSeen = true
        }
        return accepted
    }

    private companion object {
        const val PALETTE_ENTRY_BYTES: Int = 3
        const val MAX_PALETTE_ENTRIES: Int = 256
        const val GREY_TRANSPARENCY_BYTES: Int = 2
        const val TRUECOLOUR_TRANSPARENCY_BYTES: Int = 6
    }
}
