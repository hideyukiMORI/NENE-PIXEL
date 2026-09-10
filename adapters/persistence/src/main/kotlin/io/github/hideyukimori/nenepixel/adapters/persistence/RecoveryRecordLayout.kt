package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import java.util.zip.CRC32

internal object RecoveryRecordLayout {
    const val MAX_RECORD_BYTE_COUNT: Int = 262_209
    const val MAX_PROBE_BYTE_COUNT: Int = MAX_RECORD_BYTE_COUNT + 1
    const val VERSION: Int = 1
    const val VERSION_OFFSET: Int = 8
    const val STATE_OFFSET: Int = 10
    const val GENERATION_OFFSET: Int = 11
    const val PAYLOAD_OFFSET: Int = 19
    const val CHECKSUM_BYTE_COUNT: Int = 4
    const val HEADER_BYTE_COUNT: Int = PAYLOAD_OFFSET + CHECKSUM_BYTE_COUNT
    const val RETIRED_BYTE_COUNT: Int = HEADER_BYTE_COUNT
    const val MIN_CANDIDATE_BYTE_COUNT: Int = 69
    const val CANDIDATE_STATE: Int = 1
    const val RETIRED_STATE: Int = 2

    fun encode(
        state: Int,
        generation: RecoveryGeneration,
        payload: ByteArray,
    ): ByteArray {
        val bytes = ByteArray(PAYLOAD_OFFSET + payload.size + CHECKSUM_BYTE_COUNT)
        MAGIC.copyInto(bytes)
        RecoveryRecordBigEndian.writeUnsignedShort(bytes, VERSION_OFFSET, VERSION)
        bytes[STATE_OFFSET] = state.toByte()
        RecoveryRecordBigEndian.writeLong(bytes, GENERATION_OFFSET, generation.value)
        payload.copyInto(bytes, PAYLOAD_OFFSET)
        val checksumOffset = bytes.size - CHECKSUM_BYTE_COUNT
        RecoveryRecordBigEndian.writeInt(bytes, checksumOffset, checksum(bytes, checksumOffset).toInt())
        return bytes
    }

    fun hasMagic(bytes: ByteArray): Boolean = MAGIC.indices.all { index -> bytes[index] == MAGIC[index] }

    fun version(bytes: ByteArray): Int = RecoveryRecordBigEndian.readUnsignedShort(bytes, VERSION_OFFSET)

    fun state(bytes: ByteArray): Int = RecoveryRecordBigEndian.unsignedByte(bytes[STATE_OFFSET])

    fun generation(bytes: ByteArray): RecoveryGenerationResult =
        RecoveryGeneration.create(RecoveryRecordBigEndian.readLong(bytes, GENERATION_OFFSET))

    fun payload(bytes: ByteArray): ByteArray = bytes.copyOfRange(PAYLOAD_OFFSET, bytes.size - CHECKSUM_BYTE_COUNT)

    fun hasValidChecksum(bytes: ByteArray): Boolean {
        val checksumOffset = bytes.size - CHECKSUM_BYTE_COUNT
        return checksum(bytes, checksumOffset) == RecoveryRecordBigEndian.readInt(bytes, checksumOffset).toUInt()
    }

    private fun checksum(
        bytes: ByteArray,
        endExclusive: Int,
    ): UInt {
        val checksum = CRC32()
        checksum.update(bytes, 0, endExclusive)
        return checksum.value.toUInt()
    }

    private val MAGIC: ByteArray = byteArrayOf(0x4e, 0x45, 0x4e, 0x45, 0x52, 0x45, 0x43, 0x00)
}
