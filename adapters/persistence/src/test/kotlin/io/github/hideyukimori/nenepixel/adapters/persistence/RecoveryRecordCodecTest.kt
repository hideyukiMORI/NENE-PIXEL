package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.zip.CRC32

internal class RecoveryRecordCodecTest {
    @Test
    fun `retired generation one matches exact golden bytes`() {
        val encoded = encodedRetired(1L)

        assertEquals(
            "4e454e45524543000002020000000000000001ab096daf",
            encoded.toHexadecimal(),
        )
        val decoded = RecoveryRecordCodec.decode(encoded) as RecoveryDecodeResult.Accepted
        assertEquals(RecoveryRecord.Retired(PersistenceTestValues.generation(1L)), decoded.record)
    }

    @Test
    fun `minimal candidate is deterministic and round trips nested v2`() {
        val first = encodedCandidate(3L)
        val second = encodedCandidate(3L)

        assertArrayEquals(first, second)
        assertEquals(77, first.size)
        val record = (RecoveryRecordCodec.decode(first) as RecoveryDecodeResult.Accepted).record
        assertEquals(
            RecoveryRecord.Candidate(
                PersistenceTestValues.generation(3L),
                DocumentImportSource.Current(PersistenceTestValues.minimalDocument),
            ),
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

        assertEquals(66_628, encoded.bytes.size)
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
    fun `envelope and nested project versions must pair exactly`() {
        val mismatched =
            encodedCandidate(1L)
                .also {
                    it[27] = 0
                    it[28] = 1
                }.withUpdatedChecksum()

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(mismatched),
        )

        val reverseMismatch =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V1_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                PersistenceTestValues.generation(1L),
                ProjectFormatCodec.encode(PersistenceTestValues.minimalDocument).copyBytes(),
            )
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(reverseMismatch),
        )
    }

    @Test
    fun `envelope versions enforce their own exact candidate bounds`() {
        val generation = PersistenceTestValues.generation(2L)
        val minimumV1 =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V1_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                generation,
                ProjectFormatCodec.encodeLegacySource(PersistenceTestValues.minimalLegacySource()).copyBytes(),
            )
        assertEquals(69, minimumV1.size)
        assertEquals(
            RecoveryRecord.Candidate(
                generation,
                DocumentImportSource.Legacy(PersistenceTestValues.minimalLegacySource()),
            ),
            (RecoveryRecordCodec.decode(minimumV1) as RecoveryDecodeResult.Accepted).record,
        )

        val overV2Bound =
            (
                RecoveryRecordCodec.encodeCandidate(
                    generation,
                    PersistenceTestValues.maximumDocument(),
                ) as RecoveryEncodeResult.Encoded
            ).bytes.copyOf(RecoveryRecordLayout.V2_MAX_CANDIDATE_BYTE_COUNT + 1).withUpdatedChecksum()
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT),
            RecoveryRecordCodec.decode(overV2Bound),
        )
    }

    @Test
    fun `envelope v1 decodes only an exact nested v1 legacy source`() {
        val generation = PersistenceTestValues.generation(4L)
        val source = PersistenceTestValues.maximumLegacySource()
        val bytes =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V1_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                generation,
                ProjectFormatCodec.encodeLegacySource(source).copyBytes(),
            )

        assertEquals(RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT, bytes.size)
        assertEquals(
            RecoveryRecord.Candidate(generation, DocumentImportSource.Legacy(source)),
            (RecoveryRecordCodec.decode(bytes) as RecoveryDecodeResult.Accepted).record,
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
