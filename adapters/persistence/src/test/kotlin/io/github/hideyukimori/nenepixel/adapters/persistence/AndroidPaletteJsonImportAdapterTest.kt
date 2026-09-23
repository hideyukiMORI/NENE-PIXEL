package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream

internal class AndroidPaletteJsonImportAdapterTest {
    @Test
    fun `golden two slot bytes import the definition and close the input`() =
        runBlocking {
            val content = MemoryProjectContent(MINIMAL_GOLDEN.encodeToByteArray())
            assertEquals(PaletteJsonImportOutcome.Imported(minimalDefinition()), adapter(content).import())
            assertTrue(content.inputClosed)
            assertEquals(1, content.openInputCalls)
        }

    @Test
    fun `bytes beyond the probe bound map to resource limit`() =
        runBlocking {
            val overProbe = MemoryProjectContent(ByteArray(PaletteJsonBytes.MAX_PROBE_BYTE_COUNT + 1))
            val atProbe = MemoryProjectContent(ByteArray(PaletteJsonBytes.MAX_PROBE_BYTE_COUNT))
            val expected = PaletteJsonImportOutcome.Failed(ProjectStorageFailure.ResourceLimitExceeded)
            assertEquals(expected, adapter(overProbe).import())
            assertEquals(expected, adapter(atProbe).import())
        }

    @Test
    fun `invalid UTF-8 maps to invalid palette JSON`() =
        runBlocking {
            val content = MemoryProjectContent(byteArrayOf(0xc3.toByte(), 0x28))
            assertEquals(
                PaletteJsonImportOutcome.Failed(ProjectStorageFailure.InvalidPaletteJson),
                adapter(content).import(),
            )
        }

    @Test
    fun `unsupported version maps to unsupported palette JSON version`() =
        runBlocking {
            val versionTwo = MINIMAL_GOLDEN.replace("\"version\":1", "\"version\":2")
            val content = MemoryProjectContent(versionTwo.encodeToByteArray())
            assertEquals(
                PaletteJsonImportOutcome.Failed(ProjectStorageFailure.UnsupportedPaletteJsonVersion),
                adapter(content).import(),
            )
        }

    @Test
    fun `picker cancellation opens no input and picker failure passes through`() =
        runBlocking {
            val content = MemoryProjectContent(MINIMAL_GOLDEN.encodeToByteArray())
            val pickerFailure = ProjectStorageFailure.PermissionDenied(ProjectTransportPhase.PICKER_RESULT)
            assertEquals(
                PaletteJsonImportOutcome.Cancelled,
                adapter(content, InternalPickerResult.Cancelled).import(),
            )
            assertEquals(
                PaletteJsonImportOutcome.Failed(pickerFailure),
                adapter(content, InternalPickerResult.Failed(pickerFailure)).import(),
            )
            assertEquals(0, content.openInputCalls)
        }

    @Test
    fun `source read failure maps to its transport phase`() =
        runBlocking {
            val content =
                MemoryProjectContent(inputFactory = {
                    object : InputStream() {
                        override fun read(): Int = throw IOException("test read")
                    }
                })
            assertEquals(
                PaletteJsonImportOutcome.Failed(ProjectStorageFailure.IoFailure(ProjectTransportPhase.SOURCE_READ)),
                adapter(content).import(),
            )
        }

    @Test
    fun `picker receives a palette JSON open request`() =
        runBlocking {
            val content = MemoryProjectContent(MINIMAL_GOLDEN.encodeToByteArray())
            val requests = mutableListOf<DocumentOpenRequest>()
            val picker =
                object : ProjectPickerAccess {
                    override suspend fun createDocument(request: DocumentCreationRequest): InternalPickerResult =
                        error("Import must not create an output")

                    override suspend fun openDocument(request: DocumentOpenRequest): InternalPickerResult {
                        requests += request
                        return InternalPickerResult.Selected(TestLocation)
                    }
                }
            AndroidPaletteJsonImportAdapter.create(content, picker, Dispatchers.Unconfined).import()
            assertEquals(listOf(DocumentOpenRequest(DocumentOutputFormat.PALETTE_JSON)), requests)
            assertEquals(0, content.openOutputCalls)
        }

    private fun adapter(
        content: ProjectContentAccess,
        pickerResult: InternalPickerResult = InternalPickerResult.Selected(TestLocation),
    ) = AndroidPaletteJsonImportAdapter.create(content, FixedProjectPicker(pickerResult), Dispatchers.Unconfined)

    private fun minimalDefinition(): PaletteDefinition =
        created(
            PaletteDefinition.create(
                created(Palette.create(listOf(0x00000000, 0xff0000ff.toInt()).map(PixelColor::fromPackedRgba8888))),
                created(PaletteIndex.create(0)),
            ),
        )

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected domain rejection: ${result.rejection}")
        }

    private companion object {
        const val MINIMAL_GOLDEN: String =
            "{\"format\":\"nene-pixel-palette\",\"version\":1,\"defaultIndex\":0," +
                "\"colors\":[\"#00000000\",\"#ff0000ff\"]}\n"
    }
}
