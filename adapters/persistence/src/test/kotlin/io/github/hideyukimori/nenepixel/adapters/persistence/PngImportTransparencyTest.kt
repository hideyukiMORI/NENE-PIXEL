package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertDecoded
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertImageIo
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.packed
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.palette
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.scanlines
import org.junit.jupiter.api.Test

internal class PngImportTransparencyTest {
    @Test
    fun `palette tRNS shorter than PLTE leaves the remaining entries opaque`() {
        val plte = palette(0x102030, 0x405060, 0x708090, 0xa0b0c0)
        val trns = TestPngBuilder.chunk("tRNS", byteArrayOf(0x00, 0x80.toByte()))
        val bytes = image(TestPngBuilder.header(4, 1, 2, 3), scanlines(packed(2, 0, 1, 2, 3)), plte, trns)
        val expected = intArrayOf(0x10203000, 0x40506080, 0x708090ff, 0xa0b0c0ff.toInt())
        assertDecoded(bytes, 4, 1, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `grey key at depth 2 matches the stored sample before scaling`() {
        val trns = TestPngBuilder.chunk("tRNS", byteArrayOf(0, 2))
        val bytes = image(TestPngBuilder.header(5, 1, 2, 0), scanlines(packed(2, 0, 1, 2, 3, 2)), trns)
        val expected = intArrayOf(0x000000ff, 0x555555ff, 0xaaaaaa00.toInt(), 0xffffffff.toInt(), 0xaaaaaa00.toInt())
        assertDecoded(bytes, 5, 1, expected)
        // ImageIO getRGB converts grey through its linear grey colour space; literal only.
    }

    @Test
    fun `grey key at depth 8 makes only the equal sample transparent`() {
        val trns = TestPngBuilder.chunk("tRNS", byteArrayOf(0, 0x80.toByte()))
        val bytes = image(TestPngBuilder.header(3, 1, 8, 0), scanlines(packed(8, 0x80, 0x7f, 0x81)), trns)
        val expected = intArrayOf(0x80808000.toInt(), 0x7f7f7fff, 0x818181ff.toInt())
        assertDecoded(bytes, 3, 1, expected)
        // ImageIO getRGB converts grey through its linear grey colour space; literal only.
    }

    @Test
    fun `truecolour key makes only the pixel equal in all three channels transparent`() {
        val trns = TestPngBuilder.chunk("tRNS", byteArrayOf(0, 0x10, 0, 0x20, 0, 0x30))
        val row = packed(8, 0x10, 0x20, 0x30, 0x11, 0x20, 0x30, 0x10, 0x21, 0x30, 0x10, 0x20, 0x31)
        val bytes = image(TestPngBuilder.header(4, 1, 8, 2), scanlines(row), trns)
        val expected = intArrayOf(0x10203000, 0x112030ff, 0x102130ff, 0x102031ff)
        assertDecoded(bytes, 4, 1, expected)
        assertImageIo(bytes, expected)
    }

    private fun image(
        header: ByteArray,
        rows: ByteArray,
        vararg beforeData: ByteArray,
    ): ByteArray = TestPngBuilder.image(header, rows, beforeData.toList())
}
