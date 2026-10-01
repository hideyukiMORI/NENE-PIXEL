package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertDecoded
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertImageIo
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.packed
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.palette
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.scanlines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PngImportDecoderTest {
    private val white = 0xffffffff.toInt()
    private val black = 0x000000ff

    @Test
    fun `grey at depth 1 with spare bits replicates to 0 and 255`() {
        val bytes =
            image(
                header(10, 2, 1, 0),
                scanlines(packed(1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1), packed(1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0)),
            )
        val w = white
        val b = black
        val expected = intArrayOf(w, b, w, w, b, b, w, b, w, w, b, w, b, b, w, w, b, w, b, b)
        assertDecoded(bytes, 10, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `grey at depth 2 with spare bits replicates by 85`() {
        val bytes = image(header(3, 2, 2, 0), scanlines(packed(2, 0, 1, 2), packed(2, 3, 2, 1)))
        val expected = intArrayOf(0x000000ff, 0x555555ff, 0xaaaaaaff.toInt(), white, 0xaaaaaaff.toInt(), 0x555555ff)
        assertDecoded(bytes, 3, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `grey at depth 4 with spare bits replicates by 17`() {
        val bytes = image(header(3, 2, 4, 0), scanlines(packed(4, 0, 5, 15), packed(4, 10, 1, 8)))
        val expected =
            intArrayOf(0x000000ff, 0x555555ff, white, 0xaaaaaaff.toInt(), 0x111111ff, 0x888888ff.toInt())
        assertDecoded(bytes, 3, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `grey at depth 8 is taken as stored`() {
        val bytes = image(header(2, 2, 8, 0), scanlines(packed(8, 0x12, 0xfe), packed(8, 0x80, 0x01)))
        val expected = intArrayOf(0x121212ff, 0xfefefeff.toInt(), 0x808080ff.toInt(), 0x010101ff)
        assertDecoded(bytes, 2, 2, expected)
        // ImageIO getRGB converts grey through its linear grey colour space; literal only.
    }

    @Test
    fun `truecolour at depth 8 is opaque`() {
        val bytes =
            image(
                header(2, 2, 8, 2),
                scanlines(packed(8, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60), packed(8, 1, 2, 3, 0xfd, 0xfe, 0xff)),
            )
        val expected = intArrayOf(0x102030ff, 0x405060ff, 0x010203ff, 0xfdfeffff.toInt())
        assertDecoded(bytes, 2, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `palette at depth 1 with spare bits`() {
        val plte = palette(0x102030, 0xa0b0c0)
        val bytes = image(header(3, 2, 1, 3), scanlines(packed(1, 1, 0, 1), packed(1, 0, 0, 1)), plte)
        val expected =
            intArrayOf(0xa0b0c0ff.toInt(), 0x102030ff, 0xa0b0c0ff.toInt(), 0x102030ff, 0x102030ff, 0xa0b0c0ff.toInt())
        assertDecoded(bytes, 3, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `palette at depth 2 with spare bits`() {
        val plte = palette(0x000001, 0x111111, 0x222222, 0x333333)
        val bytes = image(header(5, 1, 2, 3), scanlines(packed(2, 0, 1, 2, 3, 1)), plte)
        val expected = intArrayOf(0x000001ff, 0x111111ff, 0x222222ff, 0x333333ff, 0x111111ff)
        assertDecoded(bytes, 5, 1, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `palette at depth 4 with spare bits`() {
        val plte = palette(0x010203, 0x040506, 0x070809)
        val bytes = image(header(3, 2, 4, 3), scanlines(packed(4, 0, 2, 1), packed(4, 2, 2, 0)), plte)
        val expected = intArrayOf(0x010203ff, 0x070809ff, 0x040506ff, 0x070809ff, 0x070809ff, 0x010203ff)
        assertDecoded(bytes, 3, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `palette at depth 8`() {
        val plte = palette(0xff0000, 0x00ff00, 0x0000ff)
        val bytes = image(header(2, 2, 8, 3), scanlines(packed(8, 2, 0), packed(8, 1, 1)), plte)
        val expected = intArrayOf(0x0000ffff, 0xff0000ff.toInt(), 0x00ff00ff, 0x00ff00ff)
        assertDecoded(bytes, 2, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `grey with alpha at depth 8 is not premultiplied`() {
        val bytes = image(header(2, 1, 8, 4), scanlines(packed(8, 0x40, 0x80, 0xc0, 0x00)))
        val expected = intArrayOf(0x40404080, 0xc0c0c000.toInt())
        assertDecoded(bytes, 2, 1, expected)
        // ImageIO getRGB converts grey through its linear grey colour space; literal only.
    }

    @Test
    fun `truecolour with alpha at depth 8 keeps RGB under alpha 0`() {
        val bytes =
            image(
                header(2, 2, 8, 6),
                scanlines(packed(8, 1, 2, 3, 4, 5, 6, 7, 0), packed(8, 0xfa, 0xfb, 0xfc, 0xff, 0x80, 0x81, 0x82, 1)),
            )
        val expected = intArrayOf(0x01020304, 0x05060700, 0xfafbfcff.toInt(), 0x80818201.toInt())
        assertDecoded(bytes, 2, 2, expected)
        assertImageIo(bytes, expected)
    }

    @Test
    fun `IDAT split into parts or with empty IDAT chunks decodes the same`() {
        val rows = scanlines(packed(8, 1, 2, 3, 4, 5, 6, 7, 8), packed(8, 9, 10, 11, 12, 13, 14, 15, 16))
        val header = TestPngBuilder.header(2, 2, 8, 6)
        val expected = intArrayOf(0x01020304, 0x05060708, 0x090a0b0c, 0x0d0e0f10)
        listOf(1, 2, 5).forEach { parts ->
            assertDecoded(TestPngBuilder.image(header, rows, parts = parts), 2, 2, expected)
        }
        val halves = TestPngBuilder.dataChunks(TestPngBuilder.deflate(rows), 2)
        val empty = TestPngBuilder.chunk("IDAT")
        val withEmpty =
            TestPngBuilder.png(
                listOf(header, empty, halves[0], empty, halves[1], empty, TestPngBuilder.end()),
            )
        assertDecoded(withEmpty, 2, 2, expected)
    }

    @Test
    fun `1024 by 1 and 1 by 1024 decode`() {
        val samples = IntArray(1024) { it % 256 }
        val wide = image(header(1024, 1, 8, 0), scanlines(packed(8, *samples)))
        val tall = image(header(1, 1024, 8, 0), scanlines(*Array(1024) { packed(8, it % 256) }))
        val expected = IntArray(1024) { (it % 256) * 0x01010100 or 0xff }
        assertEquals(0x000000ff, expected[0])
        assertEquals(0xffffffff.toInt(), expected[1023])
        assertDecoded(wide, 1024, 1, expected)
        assertDecoded(tall, 1, 1024, expected)
    }

    private fun header(
        width: Int,
        height: Int,
        depth: Int,
        type: Int,
    ): ByteArray = TestPngBuilder.header(width, height, depth, type)

    private fun image(
        header: ByteArray,
        rows: ByteArray,
        vararg beforeData: ByteArray,
    ): ByteArray = TestPngBuilder.image(header, rows, beforeData.toList())
}
