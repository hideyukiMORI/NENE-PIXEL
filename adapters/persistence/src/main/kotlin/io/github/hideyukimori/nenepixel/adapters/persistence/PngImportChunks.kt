package io.github.hideyukimori.nenepixel.adapters.persistence

import java.util.zip.CRC32

/**
 * Reads one PNG chunk with every read range checked (ADR 0033). Nothing is allocated for the data
 * and nothing is thrown.
 */
internal object PngImportChunks {
    private const val LENGTH_BYTES: Int = 4
    private const val TYPE_BYTES: Int = 4
    private const val INT_BYTES: Int = 4
    private const val BYTE_BITS: Int = 8
    private const val BYTE_MASK: Long = 0xFF

    /**
     * The chunk at [offset], or null when its length is negative or larger than the bytes that
     * remain, its type is not four ASCII letters, or its CRC does not match.
     */
    fun read(
        encoded: ByteArray,
        offset: Int,
    ): PngImportChunk? =
        unsigned(encoded, offset)
            ?.takeIf { length -> fits(encoded, offset, length) }
            ?.toInt()
            ?.takeIf { (offset + LENGTH_BYTES until offset + LENGTH_BYTES + TYPE_BYTES).all { isLetter(encoded[it]) } }
            ?.let { length ->
                PngImportChunk(
                    String(encoded, offset + LENGTH_BYTES, TYPE_BYTES, Charsets.US_ASCII),
                    offset + LENGTH_BYTES + TYPE_BYTES,
                    length,
                )
            }?.takeIf { hasMatchingCrc(encoded, it) }

    /** True when length, type, [length] data bytes and the CRC lie inside [encoded]. */
    private fun fits(
        encoded: ByteArray,
        offset: Int,
        length: Long,
    ): Boolean = length <= Int.MAX_VALUE && offset + LENGTH_BYTES + TYPE_BYTES + length + INT_BYTES <= encoded.size

    private fun isLetter(byte: Byte): Boolean = byte.toInt().toChar().let { it in 'A'..'Z' || it in 'a'..'z' }

    private fun hasMatchingCrc(
        encoded: ByteArray,
        chunk: PngImportChunk,
    ): Boolean {
        val crc = CRC32()
        crc.update(encoded, chunk.dataOffset - TYPE_BYTES, TYPE_BYTES + chunk.length)
        return unsigned(encoded, chunk.dataOffset + chunk.length) == crc.value
    }

    /**
     * Calls [action] with the data offset and length of each `IDAT` chunk in [span], in order,
     * including chunks of length 0. The walk has already checked every length and CRC in [span], so
     * only the lengths are read here and nothing is copied.
     */
    fun forEachData(
        encoded: ByteArray,
        span: PngImportDataSpan,
        action: (dataOffset: Int, length: Int) -> Unit,
    ) {
        var offset = span.start
        while (offset < span.end) {
            val length = checkNotNull(unsigned(encoded, offset)).toInt()
            val dataOffset = offset + LENGTH_BYTES + TYPE_BYTES
            action(dataOffset, length)
            offset = dataOffset + length + INT_BYTES
        }
    }

    /** The unsigned big-endian four-byte value at [offset], or null when it does not lie inside [bytes]. */
    private fun unsigned(
        bytes: ByteArray,
        offset: Int,
    ): Long? =
        if (offset < 0 || offset > bytes.size - INT_BYTES) {
            null
        } else {
            (0 until INT_BYTES).fold(0L) { value, step ->
                (value shl BYTE_BITS) or (bytes[offset + step].toLong() and BYTE_MASK)
            }
        }
}
