package io.github.hideyukimori.nenepixel.adapters.persistence

import kotlin.math.abs

/**
 * Reverses the PNG row filters 0 (None), 1 (Sub), 2 (Up), 3 (Average) and 4 (Paeth) in place
 * (ADR 0033). The left neighbour of a byte is `max(1, bits per pixel / 8)` bytes before it.
 */
internal object PngImportUnfilter {
    private const val BYTE_BITS: Int = 8
    private const val BYTE_MASK: Int = 0xFF
    private const val SUB: Int = 1
    private const val UP: Int = 2
    private const val AVERAGE: Int = 3
    private const val PAETH: Int = 4

    /**
     * Reverses the filter of every row of [data], the inflated stream of [header]. False when a
     * filter byte is above 4; the rows after it are left as they are.
     */
    fun unfilter(
        data: ByteArray,
        header: PngImportHeader,
    ): Boolean {
        val stride = 1 + header.rowByteCount
        val distance = maxOf(1, header.colorType.samplesPerPixel * header.bitDepth / BYTE_BITS)
        return (0 until header.height).all { row -> unfilterRow(data, row * stride, stride, distance) }
    }

    private fun unfilterRow(
        data: ByteArray,
        start: Int,
        stride: Int,
        distance: Int,
    ): Boolean {
        val filter = data[start].toInt() and BYTE_MASK
        val accepted = filter <= PAETH
        if (accepted) {
            val hasUp = start > 0
            for (at in start + 1 until start + stride) {
                val hasLeft = at - distance > start
                val left = byteAt(data, at - distance, hasLeft)
                val up = byteAt(data, at - stride, hasUp)
                val upLeft = byteAt(data, at - stride - distance, hasLeft && hasUp)
                data[at] = ((data[at].toInt() and BYTE_MASK) + predictor(filter, left, up, upLeft)).toByte()
            }
        }
        return accepted
    }

    private fun byteAt(
        data: ByteArray,
        at: Int,
        exists: Boolean,
    ): Int = if (exists) data[at].toInt() and BYTE_MASK else 0

    /** The predictor of [filter] (0 to 4) from the reconstructed neighbours. */
    private fun predictor(
        filter: Int,
        left: Int,
        up: Int,
        upLeft: Int,
    ): Int =
        when (filter) {
            SUB -> {
                left
            }

            UP -> {
                up
            }

            AVERAGE -> {
                (left + up) / 2
            }

            PAETH -> {
                paeth(left, up, upLeft)
            }

            else -> {
                0
            }
        }

    private fun paeth(
        left: Int,
        up: Int,
        upLeft: Int,
    ): Int {
        val estimate = left + up - upLeft
        val toLeft = abs(estimate - left)
        val toUp = abs(estimate - up)
        val toUpLeft = abs(estimate - upLeft)
        return when {
            toLeft <= toUp && toLeft <= toUpLeft -> {
                left
            }

            toUp <= toUpLeft -> {
                up
            }

            else -> {
                upLeft
            }
        }
    }
}
