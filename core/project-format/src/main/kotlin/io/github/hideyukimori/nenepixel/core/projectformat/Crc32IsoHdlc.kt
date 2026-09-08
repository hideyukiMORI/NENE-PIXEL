package io.github.hideyukimori.nenepixel.core.projectformat

internal object Crc32IsoHdlc {
    private const val INITIAL: UInt = 0xffffffffu
    private const val REFLECTED_POLYNOMIAL: UInt = 0xedb88320u
    private const val BYTE_MASK: Int = 0xff

    fun checksum(
        source: ByteArray,
        endExclusive: Int = source.size,
    ): UInt {
        var crc = INITIAL
        repeat(endExclusive) { index ->
            crc = update(crc, source[index].toInt() and BYTE_MASK)
        }
        return crc xor INITIAL
    }

    fun checksum(
        source: ProjectFormatBytes,
        endExclusive: Int,
    ): UInt {
        var crc = INITIAL
        repeat(endExclusive) { index ->
            crc = update(crc, source.byteAt(index).toInt() and BYTE_MASK)
        }
        return crc xor INITIAL
    }

    private fun update(
        current: UInt,
        unsignedByte: Int,
    ): UInt {
        var crc = current xor unsignedByte.toUInt()
        repeat(Byte.SIZE_BITS) {
            crc = if (crc and 1u == 0u) crc shr 1 else (crc shr 1) xor REFLECTED_POLYNOMIAL
        }
        return crc
    }
}
