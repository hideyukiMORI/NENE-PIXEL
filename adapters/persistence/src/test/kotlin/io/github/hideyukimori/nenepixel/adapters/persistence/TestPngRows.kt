package io.github.hideyukimori.nenepixel.adapters.persistence

import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Builds PNG scanlines for the import decoder tests: packs samples of any bit depth (high bits
 * first) and applies the five row filters independently of the decoder under test.
 */
internal object TestPngRows {
    private const val BYTE_BITS: Int = 8
    private const val BYTE_MASK: Int = 0xFF

    /** [samples] of [depth] bits packed into one row, high bits first, the unused bits zero. */
    fun packed(
        depth: Int,
        vararg samples: Int,
    ): ByteArray {
        val row = ByteArray((samples.size * depth + BYTE_BITS - 1) / BYTE_BITS)
        samples.forEachIndexed { index, sample ->
            val bit = index * depth
            val shift = BYTE_BITS - depth - bit % BYTE_BITS
            row[bit / BYTE_BITS] = (row[bit / BYTE_BITS].toInt() or (sample shl shift)).toByte()
        }
        return row
    }

    /** A `PLTE` chunk of [colors], each `0xRRGGBB`. */
    fun palette(vararg colors: Int): ByteArray =
        TestPngBuilder.chunk(
            "PLTE",
            colors.flatMap { listOf((it shr 16).toByte(), (it shr BYTE_BITS).toByte(), it.toByte()) }.toByteArray(),
        )

    /** [rows] each behind filter byte 0. */
    fun scanlines(vararg rows: ByteArray): ByteArray = filtered(rows.toList(), List(rows.size) { 0 }, 1)

    /** [rows] each filtered with the filter of the same position in [filters]. */
    fun filtered(
        rows: List<ByteArray>,
        filters: List<Int>,
        distance: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        rows.forEachIndexed { index, row ->
            out.write(filters[index])
            out.write(filterRow(row, rows.getOrNull(index - 1), filters[index], distance))
        }
        return out.toByteArray()
    }

    private fun filterRow(
        row: ByteArray,
        previous: ByteArray?,
        filter: Int,
        distance: Int,
    ): ByteArray =
        ByteArray(row.size) { at ->
            val left = if (at >= distance) row[at - distance].toInt() and BYTE_MASK else 0
            val up = previous?.let { it[at].toInt() and BYTE_MASK } ?: 0
            val upLeft = if (at >= distance && previous != null) previous[at - distance].toInt() and BYTE_MASK else 0
            val predicted =
                listOf(0, left, up, (left + up) / 2, paeth(left, up, upLeft))[filter]
            ((row[at].toInt() and BYTE_MASK) - predicted).toByte()
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
        return if (toLeft <= toUp && toLeft <= toUpLeft) {
            left
        } else if (toUp <= toUpLeft) {
            up
        } else {
            upLeft
        }
    }
}
