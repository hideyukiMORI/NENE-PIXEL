package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertUnsupported
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.packed
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.palette
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.scanlines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

internal class PngImportRejectionTest {
    private val header = TestPngBuilder.header(2, 2, 8, 6)
    private val rows = scanlines(packed(8, 1, 2, 3, 4, 5, 6, 7, 8), packed(8, 9, 10, 11, 12, 13, 14, 15, 16))

    @Test
    fun `a palette index beyond PLTE is unsupported`() {
        val plte = palette(0x102030, 0x405060)
        assertUnsupported(
            TestPngBuilder.image(TestPngBuilder.header(3, 1, 2, 3), scanlines(packed(2, 0, 1, 2)), listOf(plte)),
        )
        val three = palette(0x102030, 0x405060, 0x708090)
        assertUnsupported(
            TestPngBuilder.image(TestPngBuilder.header(2, 1, 8, 3), scanlines(packed(8, 2, 3)), listOf(three)),
        )
    }

    @Test
    fun `inflated data shorter than the rows is unsupported`() {
        assertUnsupported(TestPngBuilder.image(header, rows.copyOf(rows.size - 1)))
    }

    @Test
    fun `inflated data longer than the rows by one byte is unsupported`() {
        assertUnsupported(TestPngBuilder.image(header, rows + byteArrayOf(0)))
    }

    @Test
    fun `a broken zlib stream is unsupported`() {
        val compressed = TestPngBuilder.deflate(rows)
        val badHeader = compressed.copyOf().also { it[0] = 0 }
        val badChecksum = compressed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val truncated = compressed.copyOf(compressed.size - 4)
        listOf(badHeader, badChecksum, truncated).forEach { data ->
            assertUnsupported(
                TestPngBuilder.png(listOf(header) + TestPngBuilder.dataChunks(data) + TestPngBuilder.end()),
            )
        }
    }

    @Test
    fun `a zlib stream that asks for a dictionary is unsupported`() {
        val deflater = Deflater()
        deflater.setDictionary(byteArrayOf(1, 2, 3, 4))
        deflater.setInput(rows)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        assertUnsupported(
            TestPngBuilder.png(listOf(header) + TestPngBuilder.dataChunks(out.toByteArray()) + TestPngBuilder.end()),
        )
    }

    @Test
    fun `filter byte 5 is unsupported`() {
        val filtered = rows.copyOf().also { it[rows.size / 2] = 5 }
        assertEquals(5, filtered[9].toInt())
        assertUnsupported(TestPngBuilder.image(header, filtered))
    }
}
