package io.github.hideyukimori.nenepixel.adapters.persistence

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Builds PNG byte arrays for the import reader tests from any list of chunks, independent of the
 * colour type and bit depth. Every chunk carries a correct CRC unless [corruptedChunk] is used.
 */
internal object TestPngBuilder {
    private const val HEADER_BYTES: Int = 13

    /** The offset of the interlace byte inside [headerData]; tests set it to build an Adam7 header. */
    const val INTERLACE_POSITION: Int = 12

    val signature: ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** The signature followed by [chunks], each a complete chunk from [chunk] or a sibling. */
    fun png(chunks: List<ByteArray>): ByteArray =
        chunks
            .fold(ByteArrayOutputStream().apply { write(signature) }) { out, chunk -> out.apply { write(chunk) } }
            .toByteArray()

    /** Length, [type], [data] and the correct CRC of type and data. */
    fun chunk(
        type: String,
        data: ByteArray = ByteArray(0),
    ): ByteArray = chunkWithCrc(type, data, crcOf(type, data))

    /** Like [chunk], but the CRC is off by one. */
    fun corruptedChunk(
        type: String,
        data: ByteArray = ByteArray(0),
    ): ByteArray = chunkWithCrc(type, data, crcOf(type, data) xor 1)

    /** The 13 `IHDR` data bytes with compression, filter and interlace 0. */
    fun headerData(
        width: Int,
        height: Int,
        bitDepth: Int,
        colorType: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(HEADER_BYTES)
            .putInt(width)
            .putInt(height)
            .put(bitDepth.toByte())
            .put(colorType.toByte())
            .array()

    /** A complete `IHDR` chunk built from [headerData]. */
    fun header(
        width: Int,
        height: Int,
        bitDepth: Int,
        colorType: Int,
    ): ByteArray = chunk("IHDR", headerData(width, height, bitDepth, colorType))

    /** [scanlines] (filter bytes included) compressed into one zlib stream. */
    fun deflate(scanlines: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(scanlines)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    /** `IDAT` chunks that together hold [compressed], split into [parts] pieces in order. */
    fun dataChunks(
        compressed: ByteArray,
        parts: Int = 1,
    ): List<ByteArray> =
        (0 until parts).map { part ->
            chunk("IDAT", compressed.copyOfRange(compressed.size * part / parts, compressed.size * (part + 1) / parts))
        }

    /** An `IEND` chunk. */
    fun end(): ByteArray = chunk("IEND")

    /** `IHDR`, then [beforeData], then [scanlines] deflated into [parts] `IDAT` chunks, then `IEND`. */
    fun image(
        header: ByteArray,
        scanlines: ByteArray,
        beforeData: List<ByteArray> = emptyList(),
        parts: Int = 1,
    ): ByteArray = png(listOf(header) + beforeData + dataChunks(deflate(scanlines), parts) + end())

    private fun crcOf(
        type: String,
        data: ByteArray,
    ): Int =
        CRC32()
            .apply {
                update(type.toByteArray(Charsets.US_ASCII))
                update(data)
            }.value
            .toInt()

    private fun chunkWithCrc(
        type: String,
        data: ByteArray,
        crc: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(data.size + 12)
            .putInt(data.size)
            .put(type.toByteArray(Charsets.US_ASCII))
            .put(data)
            .putInt(crc)
            .array()
}
