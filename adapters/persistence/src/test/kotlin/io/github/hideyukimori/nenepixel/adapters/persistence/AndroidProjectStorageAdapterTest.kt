package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class AndroidProjectStorageAdapterTest {
    @Test
    fun `load returns typed current v2 source`() =
        runBlocking {
            val document = PersistenceTestValues.minimalDocument
            val content = MemoryProjectContent(ProjectFormatCodec.encode(document).copyBytes())

            assertEquals(
                ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)),
                adapter(content, InternalPickerResult.Selected(TestLocation)).load(),
            )
        }

    @Test
    fun `load preserves a maximum v1 source and verified copy writes exact canonical bytes`() =
        runBlocking {
            val source = PersistenceTestValues.maximumLegacySource()
            val original = ProjectFormatCodec.encodeLegacySource(source).copyBytes()
            val input = MemoryProjectContent(original)
            val loaded = adapter(input, InternalPickerResult.Selected(TestLocation)).load()
            assertEquals(ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(source)), loaded)

            val output = MemoryProjectContent()
            val result = adapter(output, InternalPickerResult.Selected(TestLocation)).copyLegacySource(source)

            assertSame(LegacySourceCopyOutcome.Copied, result)
            assertArrayEquals(original, output.storedBytes())
            assertTrue(output.outputClosed)
            assertTrue(output.inputClosed)
        }

    @Test
    fun `save writes closes and validates the same fresh output`() =
        runBlocking {
            val content = MemoryProjectContent()
            val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

            val result = adapter.save(PersistenceTestValues.minimalDocument)

            assertSame(ProjectSaveOutcome.Saved, result)
            assertTrue(content.outputClosed)
            assertTrue(content.inputClosed)
            assertEquals(1, content.openOutputCalls)
            assertEquals(1, content.openInputCalls)
            assertEquals(0, content.deleteCalls)
        }

    @Test
    fun `read back mismatch keeps primary failure and reports cleanup`() =
        runBlocking {
            val content = MemoryProjectContent(readBackMutation = { it[0] = (it[0].toInt() xor 1).toByte() })
            val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

            val result = adapter.save(PersistenceTestValues.minimalDocument)

            assertEquals(
                ProjectSaveOutcome.Failed(
                    ProjectStorageFailure.ReadBackMismatch,
                    PartialOutputCleanup.DELETED,
                ),
                result,
            )
            assertEquals(1, content.deleteCalls)
        }

    @Test
    fun `known oversize rejects before opening`() =
        runBlocking {
            val content = MemoryProjectContent(knownByteCount = 262_187L)
            val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

            val result = adapter.load()

            assertEquals(ProjectLoadOutcome.Failed(ProjectStorageFailure.ResourceLimitExceeded), result)
            assertEquals(0, content.openInputCalls)
        }

    @Test
    fun `write cancellation closes and deletes before rethrow`() {
        val output = CancellingOutputStream()
        val content = MemoryProjectContent(outputFactory = { output })
        val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter.save(PersistenceTestValues.minimalDocument) }
        }
        assertTrue(output.closed)
        assertEquals(1, content.deleteCalls)
    }

    @Test
    fun `read cancellation closes before rethrow`() {
        val input = CancellingInputStream()
        val content = MemoryProjectContent(inputFactory = { input })
        val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter.load() }
        }
        assertTrue(input.closed)
    }

    @Test
    fun `picker failure does not create output`() =
        runBlocking {
            val content = MemoryProjectContent()
            val pickerFailure =
                ProjectStorageFailure.PermissionDenied(
                    io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase.PICKER_RESULT,
                )
            val adapter = adapter(content, InternalPickerResult.Failed(pickerFailure))

            val result = adapter.save(PersistenceTestValues.minimalDocument)

            assertEquals(ProjectSaveOutcome.Failed(pickerFailure, PartialOutputCleanup.NOT_NEEDED), result)
            assertEquals(0, content.openOutputCalls)
        }

    private fun adapter(
        content: ProjectContentAccess,
        pickerResult: InternalPickerResult,
    ) = AndroidProjectStorageAdapter.create(
        content,
        FixedProjectPicker(pickerResult),
        Dispatchers.Unconfined,
    )
}
