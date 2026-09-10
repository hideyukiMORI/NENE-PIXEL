package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.zip.CRC32

internal class RecoveryRecordCodecTest {
    @Test
    fun `retired generation one matches exact golden bytes`() {
        val encoded = encodedRetired(1L)

        assertEquals(
            "4e454e45524543000001020000000000000001403ed6ac",
            encoded.toHexadecimal(),
        )
        val decoded = RecoveryRecordCodec.decode(encoded) as RecoveryDecodeResult.Accepted
        assertEquals(RecoveryRecord.Retired(PersistenceTestValues.generation(1L)), decoded.record)
    }

    @Test
    fun `minimal candidate is deterministic and round trips nested v1`() {
        val first = encodedCandidate(3L)
        val second = encodedCandidate(3L)

        assertArrayEquals(first, second)
        assertEquals(69, first.size)
        val record = (RecoveryRecordCodec.decode(first) as RecoveryDecodeResult.Accepted).record
        assertEquals(
            RecoveryRecord.Candidate(PersistenceTestValues.generation(3L), PersistenceTestValues.minimalDocument),
            record,
        )
    }

    @Test
    fun `maximum candidate reaches the exact record bound`() {
        val encoded =
            RecoveryRecordCodec.encodeCandidate(
                PersistenceTestValues.generation(Long.MAX_VALUE),
                PersistenceTestValues.maximumDocument(),
            ) as RecoveryEncodeResult.Encoded

        assertEquals(RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT, encoded.bytes.size)
        assertTrueCandidate(RecoveryRecordCodec.decode(encoded.bytes), Long.MAX_VALUE)
    }

    @Test
    fun `maximum plus one rejects before header parsing`() {
        val result = RecoveryRecordCodec.decode(ByteArray(RecoveryRecordCodec.MAX_PROBE_BYTE_COUNT))

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.RESOURCE_LIMIT_EXCEEDED),
            result,
        )
    }

    @Test
    fun `zero generation and unknown state are corrupt`() {
        val zeroGeneration = encodedRetired(1L).also { bytes -> bytes.fill(0, 11, 19) }.withUpdatedChecksum()
        val unknownState = encodedRetired(1L).also { it[10] = 3 }.withUpdatedChecksum()

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(zeroGeneration),
        )
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(unknownState),
        )
    }

    @Test
    fun `outer checksum is validated before nested project bytes`() {
        val outerCorrupt = encodedCandidate(1L).also { it[19] = (it[19].toInt() xor 1).toByte() }

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(outerCorrupt),
        )
    }

    @Test
    fun `future nested project version remains unsupported after valid outer checksum`() {
        val futureNested =
            encodedCandidate(1L)
                .also {
                    it[27] = 0
                    it[28] = 2
                }.withUpdatedChecksum()

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.UNSUPPORTED_VERSION),
            RecoveryRecordCodec.decode(futureNested),
        )
    }

    @Test
    fun `retired exact length and checksum are enforced`() {
        val trailing = encodedRetired(2L).copyOf(24).withUpdatedChecksum()
        val corruptChecksum = encodedRetired(2L).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(trailing),
        )
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(corruptChecksum),
        )
    }

    private fun encodedRetired(generation: Long): ByteArray =
        (
            RecoveryRecordCodec.encodeRetired(
                PersistenceTestValues.generation(generation),
            ) as RecoveryEncodeResult.Encoded
        ).bytes

    private fun encodedCandidate(generation: Long): ByteArray =
        (
            RecoveryRecordCodec.encodeCandidate(
                PersistenceTestValues.generation(generation),
                PersistenceTestValues.minimalDocument,
            ) as RecoveryEncodeResult.Encoded
        ).bytes

    private fun assertTrueCandidate(
        result: RecoveryDecodeResult,
        generation: Long,
    ) {
        val record = (result as RecoveryDecodeResult.Accepted).record
        assertEquals(RecoveryRecord.Candidate::class, record::class)
        assertEquals(generation, record.generation.value)
    }

    private fun ByteArray.withUpdatedChecksum(): ByteArray {
        val checksumOffset = size - Int.SIZE_BYTES
        val crc = CRC32().also { it.update(this, 0, checksumOffset) }.value
        repeat(Int.SIZE_BYTES) { index ->
            this[checksumOffset + index] = (crc ushr ((Int.SIZE_BYTES - index - 1) * Byte.SIZE_BITS)).toByte()
        }
        return this
    }

    private fun ByteArray.toHexadecimal(): String = joinToString("") { byte -> "%02x".format(byte) }
}
