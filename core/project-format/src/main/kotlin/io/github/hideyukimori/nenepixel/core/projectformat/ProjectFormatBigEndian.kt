package io.github.hideyukimori.nenepixel.core.projectformat

internal object ProjectFormatBigEndian {
    private const val BYTE_MASK: Int = 0xff

    fun writeUnsignedShort(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = (value ushr Byte.SIZE_BITS).toByte()
        destination[offset + 1] = value.toByte()
    }

    fun writeLong(
        destination: ByteArray,
        offset: Int,
        value: Long,
    ) {
        repeat(Long.SIZE_BYTES) { byteIndex ->
            val shift = (Long.SIZE_BYTES - 1 - byteIndex) * Byte.SIZE_BITS
            destination[offset + byteIndex] = (value ushr shift).toByte()
        }
    }

    fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        repeat(Int.SIZE_BYTES) { byteIndex ->
            val shift = (Int.SIZE_BYTES - 1 - byteIndex) * Byte.SIZE_BITS
            destination[offset + byteIndex] = (value ushr shift).toByte()
        }
    }

    fun readUnsignedShort(
        source: ProjectFormatBytes,
        offset: Int,
    ): Int = (source.unsignedByteAt(offset) shl Byte.SIZE_BITS) or source.unsignedByteAt(offset + 1)

    fun readLong(
        source: ProjectFormatBytes,
        offset: Int,
    ): Long {
        var value = 0L
        repeat(Long.SIZE_BYTES) { byteIndex ->
            value = (value shl Byte.SIZE_BITS) or source.unsignedByteAt(offset + byteIndex).toLong()
        }
        return value
    }

    fun readInt(
        source: ProjectFormatBytes,
        offset: Int,
    ): Int {
        var value = 0
        repeat(Int.SIZE_BYTES) { byteIndex ->
            value = (value shl Byte.SIZE_BITS) or source.unsignedByteAt(offset + byteIndex)
        }
        return value
    }

    private fun ProjectFormatBytes.unsignedByteAt(index: Int): Int = byteAt(index).toInt() and BYTE_MASK
}
