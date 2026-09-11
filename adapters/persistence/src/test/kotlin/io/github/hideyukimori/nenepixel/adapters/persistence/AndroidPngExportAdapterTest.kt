package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream

internal class AndroidPngExportAdapterTest {
    @Test
    fun `maximum PNG writes closes and verifies through shared bounded transport`() =
        runBlocking {
            val content = MemoryProjectContent()
            val adapter = adapter(content)
            assertEquals(PngExportOutcome.Exported, adapter.export(PersistenceTestValues.maximumDocument()))
            assertTrue(content.inputClosed)
            assertTrue(content.outputClosed)
            assertEquals(0, content.deleteCalls)
        }

    @Test
    fun `PNG picker uses typed format and cancellation opens no output`() =
        runBlocking {
            val content = MemoryProjectContent()
            val picker =
                object : ProjectPickerAccess {
                    override suspend fun createDocument(request: DocumentCreationRequest): InternalPickerResult {
                        assertEquals(DocumentOutputFormat.PNG, request.format)
                        assertEquals("nene-pixel.png", request.suggestedName)
                        return InternalPickerResult.Cancelled
                    }

                    override suspend fun openDocument(): InternalPickerResult = error("PNG must not open a source")
                }
            assertEquals(
                PngExportOutcome.Cancelled,
                AndroidPngExportAdapter
                    .create(content, picker, Dispatchers.Unconfined)
                    .export(PersistenceTestValues.minimalDocument),
            )
            assertEquals(0, content.openOutputCalls)
        }

    @Test
    fun `read back mismatch preserves primary failure even if deletion fails`() =
        runBlocking {
            val content = MemoryProjectContent(readBackMutation = { it[0] = 0 })
            content.deleteResult = 0
            assertEquals(
                PngExportOutcome.Failed(ProjectStorageFailure.ReadBackMismatch, PartialOutputCleanup.DELETE_FAILED),
                adapter(content).export(PersistenceTestValues.minimalDocument),
            )
            assertEquals(1, content.deleteCalls)
        }

    @Test
    fun `write IO failure closes and deletes fresh output`() =
        runBlocking {
            val content =
                MemoryProjectContent(outputFactory = {
                    object : OutputStream() {
                        override fun write(value: Int): Unit = throw IOException("test write")
                    }
                })
            assertEquals(
                PngExportOutcome.Failed(
                    ProjectStorageFailure.IoFailure(ProjectTransportPhase.DESTINATION_WRITE),
                    PartialOutputCleanup.DELETED,
                ),
                adapter(content).export(PersistenceTestValues.minimalDocument),
            )
            assertEquals(1, content.deleteCalls)
        }

    @Test
    fun `write cancellation closes and deletes before rethrow`() {
        val output = CancellingOutputStream()
        val content = MemoryProjectContent(outputFactory = { output })
        assertThrows(CancellationException::class.java) {
            runBlocking { adapter(content).export(PersistenceTestValues.minimalDocument) }
        }
        assertTrue(output.closed)
        assertEquals(1, content.deleteCalls)
    }

    private fun adapter(content: ProjectContentAccess) =
        AndroidPngExportAdapter.create(
            content,
            FixedProjectPicker(InternalPickerResult.Selected(TestLocation)),
            Dispatchers.Unconfined,
        )
}
