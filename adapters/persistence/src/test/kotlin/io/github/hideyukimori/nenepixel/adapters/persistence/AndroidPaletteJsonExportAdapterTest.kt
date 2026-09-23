package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonBytes
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream

internal class AndroidPaletteJsonExportAdapterTest {
    @Test
    fun `minimal palette writes golden bytes then closes and verifies`() =
        runBlocking {
            val content = MemoryProjectContent()
            assertEquals(PaletteJsonExportOutcome.Exported, adapter(content).export(minimalDefinition()))
            assertArrayEquals(MINIMAL_GOLDEN.encodeToByteArray(), content.storedBytes())
            assertTrue(content.inputClosed)
            assertTrue(content.outputClosed)
            assertEquals(0, content.deleteCalls)
        }

    @Test
    fun `palette JSON picker uses typed format and cancellation opens no output`() =
        runBlocking {
            val content = MemoryProjectContent()
            val picker =
                object : ProjectPickerAccess {
                    override suspend fun createDocument(request: DocumentCreationRequest): InternalPickerResult {
                        assertEquals(DocumentOutputFormat.PALETTE_JSON, request.format)
                        assertEquals("nene-pixel.nenepalette.json", request.suggestedName)
                        return InternalPickerResult.Cancelled
                    }

                    override suspend fun openDocument(): InternalPickerResult = error("Export must not open a source")
                }
            assertEquals(
                PaletteJsonExportOutcome.Cancelled,
                AndroidPaletteJsonExportAdapter
                    .create(content, picker, Dispatchers.Unconfined)
                    .export(minimalDefinition()),
            )
            assertEquals(0, content.openOutputCalls)
        }

    @Test
    fun `read back mismatch preserves primary failure even if deletion fails`() =
        runBlocking {
            val content = MemoryProjectContent(readBackMutation = { it[0] = 0 })
            content.deleteResult = 0
            assertEquals(
                PaletteJsonExportOutcome.Failed(
                    ProjectStorageFailure.ReadBackMismatch,
                    PartialOutputCleanup.DELETE_FAILED,
                ),
                adapter(content).export(minimalDefinition()),
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
                PaletteJsonExportOutcome.Failed(
                    ProjectStorageFailure.IoFailure(ProjectTransportPhase.DESTINATION_WRITE),
                    PartialOutputCleanup.DELETED,
                ),
                adapter(content).export(minimalDefinition()),
            )
            assertEquals(1, content.deleteCalls)
        }

    @Test
    fun `write cancellation closes and deletes before rethrow`() {
        val output = CancellingOutputStream()
        val content = MemoryProjectContent(outputFactory = { output })
        assertThrows(CancellationException::class.java) {
            runBlocking { adapter(content).export(minimalDefinition()) }
        }
        assertTrue(output.closed)
        assertEquals(1, content.deleteCalls)
    }

    @Test
    fun `maximum 256 slot palette fits the shared bounded transport`() =
        runBlocking {
            val definition = definition(List(MAXIMUM_SLOTS) { index -> (index shl 24) or index }, MAXIMUM_SLOTS - 1)
            val expected = PaletteJsonCodec.encode(definition).copyBytes()
            assertTrue(expected.size <= PaletteJsonBytes.MAX_FILE_BYTE_COUNT)
            val content = MemoryProjectContent()
            assertEquals(PaletteJsonExportOutcome.Exported, adapter(content).export(definition))
            assertArrayEquals(expected, content.storedBytes())
            assertEquals(0, content.deleteCalls)
        }

    private fun adapter(content: ProjectContentAccess) =
        AndroidPaletteJsonExportAdapter.create(
            content,
            FixedProjectPicker(InternalPickerResult.Selected(TestLocation)),
            Dispatchers.Unconfined,
        )

    private fun minimalDefinition(): PaletteDefinition = definition(listOf(0x00000000, 0xff0000ff.toInt()), 0)

    private fun definition(
        packedColors: List<Int>,
        defaultIndex: Int,
    ): PaletteDefinition =
        created(
            PaletteDefinition.create(
                created(Palette.create(packedColors.map(PixelColor::fromPackedRgba8888))),
                created(PaletteIndex.create(defaultIndex)),
            ),
        )

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected domain rejection: ${result.rejection}")
        }

    private companion object {
        const val MAXIMUM_SLOTS: Int = 256
        const val MINIMAL_GOLDEN: String =
            "{\"format\":\"nene-pixel-palette\",\"version\":1,\"defaultIndex\":0," +
                "\"colors\":[\"#00000000\",\"#ff0000ff\"]}\n"
    }
}
