package io.github.hideyukimori.nenepixel.adapters.persistence

internal object RecoveryRecordBigEndian {
    fun writeUnsignedShort(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = (value ushr Byte.SIZE_BITS).toByte()
        bytes[offset + 1] = value.toByte()
    }

    fun readUnsignedShort(
        bytes: ByteArray,
        offset: Int,
    ): Int = unsignedByte(bytes[offset]) shl Byte.SIZE_BITS or unsignedByte(bytes[offset + 1])

    fun writeLong(
        bytes: ByteArray,
        offset: Int,
        value: Long,
    ) {
        repeat(Long.SIZE_BYTES) { index ->
            bytes[offset + index] = (value ushr ((Long.SIZE_BYTES - index - 1) * Byte.SIZE_BITS)).toByte()
        }
    }

    fun readLong(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        repeat(Long.SIZE_BYTES) { index ->
            value = value shl Byte.SIZE_BITS or unsignedByte(bytes[offset + index]).toLong()
        }
        return value
    }

    fun writeInt(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        repeat(Int.SIZE_BYTES) { index ->
            bytes[offset + index] = (value ushr ((Int.SIZE_BYTES - index - 1) * Byte.SIZE_BITS)).toByte()
        }
    }

    fun readInt(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        var value = 0
        repeat(Int.SIZE_BYTES) { index ->
            value = value shl Byte.SIZE_BITS or unsignedByte(bytes[offset + index])
        }
        return value
    }

    fun unsignedByte(value: Byte): Int = value.toInt() and UNSIGNED_BYTE_MASK

    private const val UNSIGNED_BYTE_MASK: Int = 0xff
}
