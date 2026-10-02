package io.github.hideyukimori.nenepixel.adapters.persistence

import java.util.zip.CRC32

/**
 * The shared layout of the two underlay memory records (ADR 0034): big-endian integers and a trailing
 * CRC-32 of every preceding byte, stored as an unsigned 32-bit value.
 */
internal object UnderlayMemoryLayout {
    const val IMAGE_MAGIC: Int = 0x4e505549
    const val STATE_MAGIC: Int = 0x4e505553
    const val VERSION: Int = 1
    const val MAGIC_OFFSET: Int = 0
    const val VERSION_OFFSET: Int = 4
    const val CHECKSUM_BYTE_COUNT: Int = 4
    const val IMAGE_HEADER_BYTE_COUNT: Int = 10
    const val IMAGE_BYTES_PER_PIXEL: Int = 4
    const val IMAGE_MIN_BYTE_COUNT: Int = IMAGE_HEADER_BYTE_COUNT + IMAGE_BYTES_PER_PIXEL + CHECKSUM_BYTE_COUNT
    const val IMAGE_MAX_BYTE_COUNT: Int = 4_194_318
    const val STATE_BYTE_COUNT: Int = 40
    const val DIRECTORY_NAME: String = "reference-underlays"
    const val IMAGE_FILE_EXTENSION: String = ".image"
    const val STATE_FILE_EXTENSION: String = ".state"
    const val MAX_REMEMBERED_WORKS: Int = 16
    const val MAX_IMAGE_RECORD_TOTAL_BYTE_COUNT: Long = 33_554_544L

    /** Writes the magic and the version at the start of [bytes]. */
    fun writePrefix(
        bytes: ByteArray,
        magic: Int,
    ) {
        RecoveryRecordBigEndian.writeInt(bytes, MAGIC_OFFSET, magic)
        RecoveryRecordBigEndian.writeUnsignedShort(bytes, VERSION_OFFSET, VERSION)
    }

    /** True when [bytes] start with [magic] and the known version; [bytes] must hold the prefix. */
    fun hasPrefix(
        bytes: ByteArray,
        magic: Int,
    ): Boolean =
        RecoveryRecordBigEndian.readInt(bytes, MAGIC_OFFSET) == magic &&
            RecoveryRecordBigEndian.readUnsignedShort(bytes, VERSION_OFFSET) == VERSION

    /** Writes the CRC-32 of every preceding byte into the last four bytes of [bytes]. */
    fun seal(bytes: ByteArray) {
        val checksumOffset = bytes.size - CHECKSUM_BYTE_COUNT
        RecoveryRecordBigEndian.writeInt(bytes, checksumOffset, checksum(bytes, checksumOffset))
    }

    /** The CRC-32 field stored in the last four bytes of [bytes]. */
    fun storedChecksum(bytes: ByteArray): Int = RecoveryRecordBigEndian.readInt(bytes, bytes.size - CHECKSUM_BYTE_COUNT)

    fun hasValidChecksum(bytes: ByteArray): Boolean =
        checksum(bytes, bytes.size - CHECKSUM_BYTE_COUNT) == storedChecksum(bytes)

    private fun checksum(
        bytes: ByteArray,
        endExclusive: Int,
    ): Int {
        val checksum = CRC32()
        checksum.update(bytes, 0, endExclusive)
        return checksum.value.toInt()
    }
}
