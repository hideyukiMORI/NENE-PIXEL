package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * Reads the stored samples of unfiltered PNG rows (ADR 0033). Samples of fewer than 8 bits are
 * taken from the high bits of each byte first; the unused bits at the end of a row are never read.
 */
internal class PngImportSamples(
    private val data: ByteArray,
    private val header: PngImportHeader,
) {
    private val stride: Int = 1 + header.rowByteCount
    private val mask: Int = (1 shl header.bitDepth) - 1

    /** The stored value of [channel] of the [pixel]-th pixel in row-major order, before any scaling. */
    fun sample(
        pixel: Int,
        channel: Int,
    ): Int {
        val row = pixel / header.width
        val bit = ((pixel % header.width) * header.colorType.samplesPerPixel + channel) * header.bitDepth
        val byte = data[row * stride + 1 + bit / BYTE_BITS].toInt() and BYTE_MASK
        return (byte ushr (BYTE_BITS - header.bitDepth - bit % BYTE_BITS)) and mask
    }

    private companion object {
        const val BYTE_BITS: Int = 8
        const val BYTE_MASK: Int = 0xFF
    }
}
