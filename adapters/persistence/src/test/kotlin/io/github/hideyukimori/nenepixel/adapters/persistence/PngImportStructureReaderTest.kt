package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.chunk
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.corruptedChunk
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.dataChunks
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.deflate
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.end
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.header
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.headerData
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.image
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngBuilder.png
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal class PngImportStructureReaderTest {
    @Test
    fun `every accepted colour type and depth is parsed with its row sizes`() {
        val cases =
            listOf(
                Case(0, 1, 1),
                Case(0, 2, 2),
                Case(0, 4, 3),
                Case(0, 8, 5),
                Case(2, 8, 15),
                Case(3, 1, 1),
                Case(3, 2, 2),
                Case(3, 4, 3),
                Case(3, 8, 5),
                Case(4, 8, 10),
                Case(6, 8, 20),
            )
        for (case in cases) {
            val beforeData = if (case.colorType == 3) listOf(chunk("PLTE", palette(2))) else emptyList()
            val parsed = parsed(image(header(5, 3, case.bitDepth, case.colorType), rows(3, case.rowBytes), beforeData))
            val parsedHeader = parsed.header
            assertEquals(listOf(5, 3, case.bitDepth, case.colorType), fields(parsedHeader))
            assertEquals(case.rowBytes, parsedHeader.rowByteCount)
            assertEquals(3 * (1 + case.rowBytes), parsedHeader.inflatedByteCount)
        }
    }

    @Test
    fun `largest sides are parsed`() {
        val parsed = parsed(image(header(1024, 1024, 8, 6), rows(1024, 4096)))
        assertEquals(4096, parsed.header.rowByteCount)
        assertEquals(4_195_328, parsed.header.inflatedByteCount)
    }

    @Test
    fun `the data visited in three IDAT chunks joins into the zlib stream`() {
        val compressed = deflate(rows(4, 2))
        val chunks = listOf(header(5, 4, 2, 3), chunk("PLTE", palette(4))) + dataChunks(compressed, 3) + end()
        val bytes = png(chunks)
        assertArrayEquals(compressed, joinedData(bytes, parsed(bytes)))
    }

    @Test
    fun `IDAT chunks of length 0 are visited between the others`() {
        val compressed = deflate(rows(2, 4))
        val parts = dataChunks(compressed, 2)
        val empty = chunk("IDAT")
        val chunks = listOf(header(1, 2, 8, 6), empty, parts[0], empty, parts[1], empty, end())
        val bytes = png(chunks)
        val structure = parsed(bytes)
        val lengths = mutableListOf<Int>()
        PngImportChunks.forEachData(bytes, structure.data) { _, length -> lengths += length }
        assertEquals(5, lengths.size)
        assertEquals(listOf(0, 0, 0), listOf(lengths[0], lengths[2], lengths[4]))
        assertArrayEquals(compressed, joinedData(bytes, structure))
    }

    @Test
    fun `palette and transparency data are kept for colour type 3`() {
        val entries = ByteArray(9) { it.toByte() }
        val alpha = byteArrayOf(7, 8)
        val parsed = parsed(single(3, 8, 1, chunk("PLTE", entries), chunk("tRNS", alpha)))
        assertArrayEquals(entries, parsed.palette)
        assertArrayEquals(alpha, parsed.transparency)
    }

    @Test
    fun `transparency of grey and truecolour is kept and the palette of types 2 and 6 is skipped`() {
        val grey = parsed(single(0, 8, 1, chunk("tRNS", byteArrayOf(0, 5))))
        assertArrayEquals(byteArrayOf(0, 5), grey.transparency)
        val colour = parsed(single(2, 8, 3, chunk("PLTE", palette(256)), chunk("tRNS", ByteArray(6))))
        assertNull(colour.palette)
        assertArrayEquals(ByteArray(6), colour.transparency)
        assertNull(parsed(single(6, 8, 4, chunk("PLTE", palette(3)))).palette)
    }

    @Test
    fun `unknown ancillary chunks are skipped before and after the data`() {
        val compressed = deflate(rows(1, 4))
        val chunks =
            listOf(header(1, 1, 8, 6), chunk("gAMA", ByteArray(4)), chunk("iCCP", ByteArray(5))) +
                chunk("abCd", ByteArray(3)) +
                dataChunks(compressed) +
                listOf(chunk("tEXt", "k\u0000v".toByteArray(Charsets.ISO_8859_1)), end())
        val bytes = png(chunks)
        assertArrayEquals(compressed, joinedData(bytes, parsed(bytes)))
    }

    @Test
    fun `bytes after IEND are ignored`() {
        val bytes = single(6, 8, 4) + byteArrayOf(1, 2, 3) + corruptedChunk("IHDR", ByteArray(13))
        parsed(bytes)
    }

    @Test
    fun `output of the repository PNG writer is parsed as truecolour with alpha`() {
        val parsed = parsed(PngEncoder.encode(PersistenceTestValues.minimalDocument).copyBytes())
        assertEquals(listOf(1, 1, 8, 6), fields(parsed.header))
    }

    @Test
    fun `wrong signature and empty input are unsupported`() {
        val valid = single(6, 8, 4)
        assertUnsupported(ByteArray(0))
        assertUnsupported(valid.copyOf().also { it[1] = 0x51 })
        assertUnsupported(valid.copyOfRange(1, valid.size))
    }

    @Test
    fun `every truncation of a valid file is unsupported`() {
        val beforeData = listOf(chunk("PLTE", palette(2)), chunk("tRNS", byteArrayOf(0)))
        val valid = image(header(2, 2, 8, 3), rows(2, 2), beforeData)
        for (size in 0 until valid.size) {
            assertUnsupported(valid.copyOf(size))
        }
    }

    @Test
    fun `a chunk length beyond the remaining bytes is unsupported`() {
        val prefix = png(listOf(header(1, 1, 8, 6)))
        // 0xFFFFFFFF is -1; 29 data bytes and a CRC are one byte more than the 32 that remain.
        for (length in listOf(0x7FFFFFFF, -1, Int.MIN_VALUE, 29)) {
            val oversized =
                ByteBuffer
                    .allocate(40)
                    .putInt(length)
                    .put("tEXt".toByteArray(Charsets.US_ASCII))
                    .array()
            assertUnsupported(prefix + oversized)
        }
        val hugeHeader =
            ByteBuffer
                .allocate(8)
                .putInt(-1)
                .put("IHDR".toByteArray(Charsets.US_ASCII))
                .array()
        assertUnsupported(png(emptyList()) + hugeHeader)
    }

    @Test
    fun `a wrong CRC is unsupported in every kind of chunk`() {
        val scan = rows(1, 4)
        val brokenHeader = corruptedChunk("IHDR", headerData(1, 1, 8, 6))
        assertUnsupported(png(listOf(brokenHeader) + dataChunks(deflate(scan)) + end()))
        assertUnsupported(image(header(1, 1, 8, 6), scan, listOf(corruptedChunk("gAMA", ByteArray(4)))))
        assertUnsupported(png(listOf(header(1, 1, 8, 6), corruptedChunk("IDAT", deflate(scan)), end())))
        assertUnsupported(single(3, 8, 1, corruptedChunk("PLTE", palette(1))))
        assertUnsupported(png(listOf(header(1, 1, 8, 6)) + dataChunks(deflate(scan)) + corruptedChunk("IEND")))
    }

    @Test
    fun `a chunk type that is not four letters is unsupported`() {
        assertUnsupported(single(6, 8, 4, chunk("gA1A", ByteArray(4))))
    }

    @Test
    fun `IHDR must be first, once and 13 bytes long`() {
        val scan = deflate(rows(1, 4))
        assertUnsupported(png(listOf(chunk("gAMA", ByteArray(4)), header(1, 1, 8, 6)) + dataChunks(scan) + end()))
        assertUnsupported(png(listOf(header(1, 1, 8, 6), header(1, 1, 8, 6)) + dataChunks(scan) + end()))
        for (size in listOf(12, 14)) {
            val resized = chunk("IHDR", headerData(1, 1, 8, 6).copyOf(size))
            assertUnsupported(png(listOf(resized) + dataChunks(scan) + end()))
        }
    }

    @Test
    fun `sides of zero or below are unsupported`() {
        assertUnsupported(image(header(0, 1, 8, 6), rows(1, 0)))
        assertUnsupported(image(header(1, 0, 8, 6), ByteArray(0)))
        assertUnsupported(image(header(Int.MIN_VALUE, 1, 8, 6), rows(1, 4)))
    }

    @Test
    fun `a side above 1024 is too many pixels before other checks`() {
        assertTooManyPixels(png(listOf(header(1025, 1, 8, 6))))
        assertTooManyPixels(png(listOf(header(1, 1025, 8, 6))))
        assertTooManyPixels(png(listOf(header(1025, 1, 16, 6))))
        assertTooManyPixels(png(listOf(header(1025, 0, 8, 6))))
    }

    @Test
    fun `sixteen-bit samples are unsupported`() {
        for (colorType in listOf(0, 2, 4, 6)) {
            assertUnsupported(single(colorType, 16, 8))
        }
    }

    @Test
    fun `interlace, compression and filter methods other than 0 are unsupported`() {
        for (position in 10..12) {
            val data = headerData(1, 1, 8, 6).also { it[position] = 1 }
            assertUnsupported(png(listOf(chunk("IHDR", data)) + dataChunks(deflate(rows(1, 4))) + end()))
        }
        val interlaced = headerData(1, 1, 8, 6).also { it[TestPngBuilder.INTERLACE_POSITION] = 1 }
        assertUnsupported(png(listOf(chunk("IHDR", interlaced)) + dataChunks(deflate(rows(1, 4))) + end()))
    }

    @Test
    fun `colour type and depth pairs outside the accepted set are unsupported`() {
        val pairs = listOf(0 to 3, 0 to 16, 2 to 1, 2 to 4, 3 to 16, 4 to 4, 6 to 1, 1 to 8, 5 to 8, 7 to 8, 255 to 8)
        for ((colorType, bitDepth) in pairs) {
            assertUnsupported(single(colorType, bitDepth, 8, chunk("PLTE", palette(1))))
        }
    }

    @Test
    fun `colour type 3 requires PLTE before the first IDAT`() {
        val compressed = deflate(rows(1, 1))
        assertUnsupported(single(3, 8, 1))
        assertUnsupported(png(listOf(header(1, 1, 8, 3)) + dataChunks(compressed) + chunk("PLTE", palette(1)) + end()))
    }

    @Test
    fun `PLTE length and entry count are checked`() {
        assertUnsupported(single(3, 8, 1, chunk("PLTE", ByteArray(4))))
        assertUnsupported(single(3, 8, 1, chunk("PLTE", ByteArray(0))))
        assertUnsupported(single(3, 1, 1, chunk("PLTE", palette(3))))
        assertUnsupported(single(3, 2, 1, chunk("PLTE", palette(5))))
        assertUnsupported(single(3, 8, 1, chunk("PLTE", palette(257))))
        assertUnsupported(single(2, 8, 3, chunk("PLTE", palette(257))))
        parsed(single(3, 1, 1, chunk("PLTE", palette(2))))
        parsed(single(3, 8, 1, chunk("PLTE", palette(256))))
    }

    @Test
    fun `PLTE on grey types and a second PLTE are unsupported`() {
        assertUnsupported(single(0, 8, 1, chunk("PLTE", palette(1))))
        assertUnsupported(single(4, 8, 2, chunk("PLTE", palette(1))))
        assertUnsupported(single(3, 8, 1, chunk("PLTE", palette(1)), chunk("PLTE", palette(1))))
        assertUnsupported(single(2, 8, 3, chunk("PLTE", palette(1)), chunk("PLTE", palette(1))))
    }

    @Test
    fun `tRNS for colour type 3 must follow PLTE and not be longer`() {
        assertUnsupported(single(3, 8, 1, chunk("PLTE", palette(2)), chunk("tRNS", ByteArray(3))))
        assertUnsupported(single(3, 8, 1, chunk("tRNS", ByteArray(1)), chunk("PLTE", palette(2))))
        parsed(single(3, 8, 1, chunk("PLTE", palette(2)), chunk("tRNS", ByteArray(2))))
        parsed(single(3, 8, 1, chunk("PLTE", palette(2)), chunk("tRNS", ByteArray(0))))
    }

    @Test
    fun `tRNS after IDAT and a second tRNS are unsupported`() {
        val compressed = deflate(rows(1, 3))
        val late = chunk("tRNS", ByteArray(6))
        assertUnsupported(png(listOf(header(1, 1, 8, 2)) + dataChunks(compressed) + late + end()))
        assertUnsupported(single(2, 8, 3, chunk("tRNS", ByteArray(6)), chunk("tRNS", ByteArray(6))))
    }

    @Test
    fun `tRNS on types with alpha is unsupported`() {
        assertUnsupported(single(6, 8, 4, chunk("tRNS", ByteArray(8))))
        assertUnsupported(single(4, 8, 2, chunk("tRNS", ByteArray(2))))
    }

    @Test
    fun `tRNS of grey and truecolour must have its exact length`() {
        assertUnsupported(single(0, 8, 1, chunk("tRNS", ByteArray(3))))
        assertUnsupported(single(0, 8, 1, chunk("tRNS", ByteArray(6))))
        assertUnsupported(single(2, 8, 3, chunk("tRNS", ByteArray(2))))
    }

    @Test
    fun `IDAT chunks must be present and consecutive`() {
        val parts = dataChunks(deflate(rows(1, 4)), 2)
        assertUnsupported(png(listOf(header(1, 1, 8, 6), parts[0], chunk("tEXt", ByteArray(1)), parts[1], end())))
        assertUnsupported(png(listOf(header(1, 1, 8, 6), end())))
    }

    @Test
    fun `IEND must be present and empty`() {
        val compressed = deflate(rows(1, 4))
        assertUnsupported(png(listOf(header(1, 1, 8, 6)) + dataChunks(compressed)))
        assertUnsupported(png(listOf(header(1, 1, 8, 6)) + dataChunks(compressed) + chunk("IEND", ByteArray(1))))
    }

    @Test
    fun `an unknown critical chunk is unsupported`() {
        assertUnsupported(single(6, 8, 4, chunk("MiSS", ByteArray(2))))
        val compressed = deflate(rows(1, 4))
        assertUnsupported(png(listOf(header(1, 1, 8, 6)) + dataChunks(compressed) + chunk("ZZZZ") + end()))
    }

    private class Case(
        val colorType: Int,
        val bitDepth: Int,
        val rowBytes: Int,
    )

    private fun fields(header: PngImportHeader): List<Int> =
        listOf(header.width, header.height, header.bitDepth, header.colorType.code)

    /** The data of every `IDAT` chunk that [PngImportChunks.forEachData] visits, joined in order. */
    private fun joinedData(
        bytes: ByteArray,
        structure: PngImportStructure,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        PngImportChunks.forEachData(bytes, structure.data) { offset, length -> out.write(bytes, offset, length) }
        return out.toByteArray()
    }

    private fun rows(
        height: Int,
        rowBytes: Int,
    ): ByteArray = ByteArray(height * (1 + rowBytes))

    /** A 1 by 1 image of [colorType] and [bitDepth] whose single row has [rowBytes] bytes. */
    private fun single(
        colorType: Int,
        bitDepth: Int,
        rowBytes: Int,
        vararg beforeData: ByteArray,
    ): ByteArray = image(header(1, 1, bitDepth, colorType), rows(1, rowBytes), beforeData.toList())

    private fun palette(entries: Int): ByteArray = ByteArray(entries * 3) { (it * 7).toByte() }

    private fun parsed(bytes: ByteArray): PngImportStructure =
        assertInstanceOf(PngImportStructureResult.Parsed::class.java, PngImportStructureReader.read(bytes)).structure

    private fun assertUnsupported(bytes: ByteArray) {
        assertEquals(PngImportStructureResult.Unsupported, PngImportStructureReader.read(bytes))
    }

    private fun assertTooManyPixels(bytes: ByteArray) {
        assertEquals(PngImportStructureResult.TooManyPixels, PngImportStructureReader.read(bytes))
    }
}
