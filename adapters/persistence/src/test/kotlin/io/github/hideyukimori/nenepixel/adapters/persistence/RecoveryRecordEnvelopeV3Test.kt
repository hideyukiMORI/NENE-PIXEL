package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.zip.CRC32

internal class RecoveryRecordEnvelopeV3Test {
    @Test
    fun `retired generation one is written as envelope v3 golden bytes`() {
        val encoded = encodedRetired(1L)

        assertEquals("4e454e4552454300000302000000000000000144cb0691", encoded.toHexadecimal())
        assertEquals(RecoveryRecordLayout.RETIRED_BYTE_COUNT, encoded.size)
        val decoded = RecoveryRecordCodec.decode(encoded) as RecoveryDecodeResult.Accepted
        assertEquals(RecoveryRecord.Retired(PersistenceTestValues.generation(1L)), decoded.record)
    }

    @Test
    fun `minimal candidate is written as envelope v3 around minimal project v3 golden bytes`() {
        val first = encodedCandidate(3L)

        assertArrayEquals(first, encodedCandidate(3L))
        assertEquals(MINIMAL_V3_CANDIDATE_HEX, first.toHexadecimal())
        assertEquals(RecoveryRecordLayout.V3_MIN_CANDIDATE_BYTE_COUNT, first.size)
        assertEquals(
            RecoveryRecord.Candidate(
                PersistenceTestValues.generation(3L),
                DocumentImportSource.Current(PersistenceTestValues.minimalDocument),
            ),
            (RecoveryRecordCodec.decode(first) as RecoveryDecodeResult.Accepted).record,
        )
    }

    @Test
    fun `test v2 payload helper matches the hand-built envelope v2 golden bytes`() {
        val bytes =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V2_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                PersistenceTestValues.generation(3L),
                PersistenceTestValues.v2ProjectBytes(PersistenceTestValues.minimalDocument),
            )

        assertEquals(MINIMAL_V2_CANDIDATE_HEX, bytes.toHexadecimal())
    }

    @Test
    fun `envelope v3 and nested project v2 is corrupt`() {
        val bytes =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V3_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                PersistenceTestValues.generation(1L),
                PersistenceTestValues.v2ProjectBytes(PersistenceTestValues.maximumDocument()),
            )

        // 66,628 bytes: inside the v3 length range, so the version pairing (not the length) rejects it.
        assertEquals(RecoveryRecordLayout.V2_MAX_CANDIDATE_BYTE_COUNT, bytes.size)
        assertEquals(CORRUPT, RecoveryRecordCodec.decode(bytes))
    }

    @Test
    fun `envelope v2 and nested project v3 is corrupt`() {
        val bytes =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V2_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                PersistenceTestValues.generation(1L),
                ProjectFormatCodec.encode(PersistenceTestValues.minimalDocument).copyBytes(),
            )

        // 85 bytes: inside the v2 length range, so the version pairing (not the length) rejects it.
        assertEquals(RecoveryRecordLayout.V3_MIN_CANDIDATE_BYTE_COUNT, bytes.size)
        assertEquals(CORRUPT, RecoveryRecordCodec.decode(bytes))
    }

    @Test
    fun `envelope v3 candidate one byte below the v3 minimum is corrupt`() {
        val bytes =
            RecoveryRecordLayout.encode(
                RecoveryRecordLayout.V3_VERSION,
                RecoveryRecordLayout.CANDIDATE_STATE,
                PersistenceTestValues.generation(1L),
                ByteArray(BELOW_V3_MINIMUM_PAYLOAD_BYTE_COUNT),
            )

        assertEquals(RecoveryRecordLayout.V3_MIN_CANDIDATE_BYTE_COUNT - 1, bytes.size)
        assertEquals(CORRUPT, RecoveryRecordCodec.decode(bytes))
    }

    @Test
    fun `maximum layered candidate reaches the exact shared record bound and one more byte is rejected`() {
        val encoded =
            RecoveryRecordCodec.encodeCandidate(
                PersistenceTestValues.generation(Long.MAX_VALUE),
                PersistenceTestValues.maximumLayeredDocument(),
            ) as RecoveryEncodeResult.Encoded

        assertEquals(1_182_885, encoded.bytes.size)
        assertEquals(RecoveryRecordLayout.V3_MAX_CANDIDATE_BYTE_COUNT, encoded.bytes.size)
        assertEquals(RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT, encoded.bytes.size)
        assertEquals(RecoveryRecordLayout.V3_VERSION, RecoveryRecordLayout.version(encoded.bytes))
        val record = (RecoveryRecordCodec.decode(encoded.bytes) as RecoveryDecodeResult.Accepted).record
        assertEquals(Long.MAX_VALUE, record.generation.value)

        val overBound = encoded.bytes.copyOf(encoded.bytes.size + 1).withUpdatedChecksum()
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.RESOURCE_LIMIT_EXCEEDED),
            RecoveryRecordCodec.decode(overBound),
        )
    }

    @Test
    fun `shared record bound covers every envelope version and the probe is one byte more`() {
        assertEquals(
            RecoveryRecordLayout.HEADER_BYTE_COUNT + ProjectFormatBytes.MAX_FILE_BYTE_COUNT,
            RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT,
        )
        assertEquals(1_182_886, RecoveryRecordCodec.MAX_PROBE_BYTE_COUNT)
        assertEquals(
            RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT,
            maxOf(
                RecoveryRecordLayout.V1_MAX_CANDIDATE_BYTE_COUNT,
                RecoveryRecordLayout.V2_MAX_CANDIDATE_BYTE_COUNT,
                RecoveryRecordLayout.V3_MAX_CANDIDATE_BYTE_COUNT,
            ),
        )
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.RESOURCE_LIMIT_EXCEEDED),
            RecoveryRecordCodec.decode(ByteArray(RecoveryRecordCodec.MAX_PROBE_BYTE_COUNT)),
        )
    }

    @Test
    fun `unknown envelope version four is unsupported`() {
        val bytes = encodedRetired(1L).also { it[9] = 4 }.withUpdatedChecksum()

        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.UNSUPPORTED_VERSION),
            RecoveryRecordCodec.decode(bytes),
        )
    }

    private fun encodedRetired(generation: Long): ByteArray {
        val result = RecoveryRecordCodec.encodeRetired(PersistenceTestValues.generation(generation))
        return (result as RecoveryEncodeResult.Encoded).bytes
    }

    private fun encodedCandidate(generation: Long): ByteArray =
        (
            RecoveryRecordCodec.encodeCandidate(
                PersistenceTestValues.generation(generation),
                PersistenceTestValues.minimalDocument,
            ) as RecoveryEncodeResult.Encoded
        ).bytes

    private fun ByteArray.withUpdatedChecksum(): ByteArray {
        val checksumOffset = size - Int.SIZE_BYTES
        val crc = CRC32().also { it.update(this, 0, checksumOffset) }.value
        repeat(Int.SIZE_BYTES) { index ->
            this[checksumOffset + index] = (crc ushr ((Int.SIZE_BYTES - index - 1) * Byte.SIZE_BITS)).toByte()
        }
        return this
    }

    private fun ByteArray.toHexadecimal(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        val CORRUPT = RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        const val BELOW_V3_MINIMUM_PAYLOAD_BYTE_COUNT: Int =
            RecoveryRecordLayout.V3_MIN_CANDIDATE_BYTE_COUNT - 1 - RecoveryRecordLayout.HEADER_BYTE_COUNT

        /**
         * Envelope `NENEREC\0`, version 3, Candidate, generation 3; then project v3 of the 1x1 minimal document
         * (palette 00000000 11223344, one layer: id 1, visible, empty name, covered, slot 1); then the outer CRC32.
         */
        const val MINIMAL_V3_CANDIDATE_HEX: String =
            "4e454e45524543000003010000000000000003" +
                "4e454e4550495800000300010001000102030405060708090a0b0c0d0e0f0000000000000000" +
                "0002000000000011223344" + "01" + "000000010100" + "01" + "01" + "5b27b2ef" +
                "b238a10a"

        /** The same document as project v2 inside envelope version 2 (77 bytes). */
        const val MINIMAL_V2_CANDIDATE_HEX: String =
            "4e454e45524543000002010000000000000003" +
                "4e454e4550495800000200010001000102030405060708090a0b0c0d0e0f0000000000000000" +
                "0002000000000011223344" + "01" + "fb4a63f3" +
                "1dca8ee7"
    }
}
