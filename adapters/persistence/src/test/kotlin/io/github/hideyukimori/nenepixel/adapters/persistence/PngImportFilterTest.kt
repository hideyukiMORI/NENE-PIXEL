package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertDecoded
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertImageIo
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.filtered
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.packed
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.palette
import org.junit.jupiter.api.Test

internal class PngImportFilterTest {
    private val filterSets: List<List<Int>> =
        (0..4).map { filter -> List(5) { filter } } +
            listOf(listOf(0, 1, 2, 3, 4), listOf(4, 3, 2, 1, 0), listOf(2, 4, 1, 3, 0))

    @Test
    fun `every filter reverses to the same truecolour with alpha pixels at distance 4`() {
        val rows = List(5) { row -> ByteArray(12) { at -> (row * 37 + at * 59 + at * at * 13).toByte() } }
        val expected =
            IntArray(15) { pixel ->
                val row = rows[pixel / 3]
                val at = pixel % 3 * 4
                (0 until 4).fold(0) { value, channel -> (value shl 8) or (row[at + channel].toInt() and 0xff) }
            }
        val header = TestPngBuilder.header(3, 5, 8, 6)
        filterSets.forEach { filters ->
            val bytes = TestPngBuilder.image(header, filtered(rows, filters, 4))
            assertDecoded(bytes, 3, 5, expected)
            assertImageIo(bytes, expected)
        }
    }

    @Test
    fun `every filter reverses to the same palette pixels at depth 2 and distance 1`() {
        val indices = List(5) { row -> IntArray(7) { column -> (row * 3 + column * 5 + column * column) % 4 } }
        val rows = indices.map { packed(2, *it) }
        val colors = intArrayOf(0x0a0b0cff, 0x405060ff, 0xc0d0e0ff.toInt(), 0xfffefdff.toInt())
        val expected = IntArray(35) { pixel -> colors[indices[pixel / 7][pixel % 7]] }
        val header = TestPngBuilder.header(7, 5, 2, 3)
        val plte = palette(0x0a0b0c, 0x405060, 0xc0d0e0, 0xfffefd)
        filterSets.forEach { filters ->
            val bytes = TestPngBuilder.image(header, filtered(rows, filters, 1), listOf(plte))
            assertDecoded(bytes, 7, 5, expected)
            assertImageIo(bytes, expected)
        }
    }
}
