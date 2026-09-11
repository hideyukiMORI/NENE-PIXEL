package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class RecoveryRecordWriterTest {
    @Test
    fun `candidate bytes are written when the read-back record satisfies the expectation`() {
        val generation = PersistenceTestValues.generation(3L)
        val bytes = encodedCandidate(generation)
        val file = WriterRecoveryFile()

        val result =
            writer(file).publish(bytes, generation) { record ->
                record == RecoveryRecord.Candidate(generation, PersistenceTestValues.minimalDocument)
            }

        assertEquals(RecordWriteResult.Written(generation), result)
        assertArrayEquals(bytes, file.snapshot())
    }

    @Test
    fun `candidate bytes are uncertain when the read-back record fails the expectation`() {
        val generation = PersistenceTestValues.generation(3L)
        val bytes = encodedCandidate(generation)
        val file = WriterRecoveryFile()

        val result =
            writer(file).publish(bytes, generation) { record ->
                record == RecoveryRecord.Retired(generation)
            }

        assertEquals(
            RecordWriteResult.Uncertain(
                RecoveryRetirementFailure.READ_BACK_MISMATCH,
                RecoveryRollbackOutcome.NOT_NEEDED,
            ),
            result,
        )
        assertArrayEquals(bytes, file.snapshot())
    }

    @Test
    fun `retired bytes are written when the read-back record satisfies the expectation`() {
        val generation = PersistenceTestValues.generation(4L)
        val bytes = encodedRetired(generation)
        val file = WriterRecoveryFile()

        val result =
            writer(file).publish(bytes, generation) { record ->
                record == RecoveryRecord.Retired(generation)
            }

        assertEquals(RecordWriteResult.Written(generation), result)
        assertArrayEquals(bytes, file.snapshot())
    }

    private fun writer(file: RecoveryAtomicFileAccess): RecoveryRecordWriter =
        RecoveryRecordWriter(file, RecoveryRecordReader(file))

    private fun encodedCandidate(generation: RecoveryGeneration): ByteArray =
        (
            RecoveryRecordCodec.encodeCandidate(
                generation,
                PersistenceTestValues.minimalDocument,
            ) as RecoveryEncodeResult.Encoded
        ).bytes

    private fun encodedRetired(generation: RecoveryGeneration): ByteArray =
        (RecoveryRecordCodec.encodeRetired(generation) as RecoveryEncodeResult.Encoded).bytes
}

private class WriterRecoveryFile : RecoveryAtomicFileAccess {
    private var bytes: ByteArray = ByteArray(0)

    fun snapshot(): ByteArray = bytes.copyOf()

    override fun openRead(): InputStream = ByteArrayInputStream(bytes.copyOf())

    override fun startWrite(): RecoveryWriteSession = WriterRecoverySession()

    private inner class WriterRecoverySession : RecoveryWriteSession {
        private val written = ByteArrayOutputStream()

        override val output: OutputStream
            get() = written

        override fun sync() = Unit

        override fun finish() {
            bytes = written.toByteArray()
        }

        override fun fail() = Unit
    }
}
