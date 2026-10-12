package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class RecoveryRecordEnvelopeV4Test {
    @Test
    fun `current Candidate and Retired use envelope4 and exact new bounds`() {
        val generation = PersistenceTestValues.generation(1)
        val minimum = encoded(RecoveryRecordCodec.encodeCandidate(generation, PersistenceTestValues.minimalDocument))
        assertEquals(94, minimum.size)
        assertEquals(4, RecoveryRecordLayout.version(minimum))
        assertEquals(
            RecoveryRecord.Candidate(generation, DocumentImportSource.Current(PersistenceTestValues.minimalDocument)),
            accepted(minimum).record,
        )
        val maximumDocument = PersistenceTestValues.maximumFramesDocument()
        val maximum = encoded(RecoveryRecordCodec.encodeCandidate(generation, maximumDocument))
        assertEquals(1_183_029, maximum.size)
        assertEquals(RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT, maximum.size)
        assertEquals(
            RecoveryRecord.Candidate(generation, DocumentImportSource.Current(maximumDocument)),
            accepted(maximum).record,
        )
        val retired = encoded(RecoveryRecordCodec.encodeRetired(generation))
        assertEquals(23, retired.size)
        assertEquals(4, RecoveryRecordLayout.version(retired))
        assertEquals(RecoveryRecord.Retired(generation), accepted(retired).record)
    }

    @Test
    fun `every nested mismatch including zero future is corrupt before nested decoding`() {
        val payload = ProjectFormatCodec.encode(PersistenceTestValues.minimalDocument).copyBytes()
        listOf(0, 1, 2, 3, 5).forEach { version ->
            val changed =
                payload.copyOf().also {
                    it[8] = 0
                    it[9] = version.toByte()
                }
            val bytes = RecoveryRecordLayout.encode(4, 1, PersistenceTestValues.generation(1), changed)
            assertEquals(RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT), RecoveryRecordCodec.decode(bytes))
        }
        val v4InV3 = RecoveryRecordLayout.encode(3, 1, PersistenceTestValues.generation(1), payload)
        assertEquals(RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT), RecoveryRecordCodec.decode(v4InV3))
    }

    @Test
    fun `new max probe minimum and future outer version retain separate rejection meanings`() {
        val minimum = RecoveryRecordLayout.encode(4, 1, PersistenceTestValues.generation(1), ByteArray(70))
        assertEquals(93, minimum.size)
        assertEquals(RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT), RecoveryRecordCodec.decode(minimum))
        val probe = ByteArray(RecoveryRecordCodec.MAX_PROBE_BYTE_COUNT)
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.RESOURCE_LIMIT_EXCEEDED),
            RecoveryRecordCodec.decode(probe),
        )
        val future = encoded(RecoveryRecordCodec.encodeRetired(PersistenceTestValues.generation(1)))
        future[9] = 5
        assertEquals(
            RecoveryDecodeResult.Rejected(RecoveryRejection.UNSUPPORTED_VERSION),
            RecoveryRecordCodec.decode(future),
        )
    }

    private fun encoded(result: RecoveryEncodeResult): ByteArray =
        assertInstanceOf(RecoveryEncodeResult.Encoded::class.java, result).bytes

    private fun accepted(bytes: ByteArray): RecoveryDecodeResult.Accepted =
        assertInstanceOf(RecoveryDecodeResult.Accepted::class.java, RecoveryRecordCodec.decode(bytes))
}
