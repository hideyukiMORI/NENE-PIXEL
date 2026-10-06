package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.packed
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.palette
import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngRows.scanlines
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportSourceRejection
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream

internal class AndroidPngImportAdapterTest {
    private val dispatchers = TestCoroutineDispatchers()

    @Test
    fun `truecolour with alpha PNG is picked through one PNG open request`() =
        runBlocking {
            val bytes =
                TestPngBuilder.image(
                    TestPngBuilder.header(2, 2, 8, 6),
                    scanlines(
                        packed(8, 1, 2, 3, 4, 5, 6, 7, 0),
                        packed(8, 0xfa, 0xfb, 0xfc, 0xff, 0x80, 0x81, 0x82, 1),
                    ),
                )
            val content = MemoryProjectContent(bytes)
            val requests = mutableListOf<DocumentOpenRequest>()
            val picker =
                object : ProjectPickerAccess {
                    override suspend fun createDocument(request: DocumentCreationRequest): InternalPickerResult =
                        error("Importing a PNG must not create an output")

                    override suspend fun openDocument(request: DocumentOpenRequest): InternalPickerResult {
                        requests += request
                        return InternalPickerResult.Selected(TestLocation)
                    }
                }
            val outcome = AndroidPngImportAdapter.create(content, picker, dispatchers.inline).pick()
            val raster = (outcome as PngImportOutcome.Picked).raster
            assertEquals(2, raster.width)
            assertEquals(2, raster.height)
            assertArrayEquals(
                intArrayOf(0x01020304, 0x05060700, 0xfafbfcff.toInt(), 0x80818201.toInt()),
                raster.copyPackedRgba8888(),
            )
            assertEquals(listOf(DocumentOpenRequest(DocumentOutputFormat.PNG)), requests)
            assertEquals(1, content.openInputCalls)
            assertEquals(0, content.openOutputCalls)
            assertTrue(content.inputClosed)
        }

    @Test
    fun `palette PNG is picked as straight RGBA`() =
        runBlocking {
            val plte = palette(0xff0000, 0x00ff00, 0x0000ff)
            val bytes =
                TestPngBuilder.image(
                    TestPngBuilder.header(2, 2, 8, 3),
                    scanlines(packed(8, 2, 0), packed(8, 1, 1)),
                    listOf(plte),
                )
            val raster = (pick(bytes) as PngImportOutcome.Picked).raster
            assertEquals(2, raster.width)
            assertEquals(2, raster.height)
            assertArrayEquals(
                intArrayOf(0x0000ffff, 0xff0000ff.toInt(), 0x00ff00ff, 0x00ff00ff),
                raster.copyPackedRgba8888(),
            )
        }

    @Test
    fun `picker cancellation and failure open no input`() =
        runBlocking {
            val content = MemoryProjectContent(TestPngBuilder.signature)
            val pickerFailure = ProjectStorageFailure.PermissionDenied(ProjectTransportPhase.PICKER_RESULT)
            assertEquals(PngImportOutcome.Cancelled, adapter(content, InternalPickerResult.Cancelled).pick())
            assertEquals(
                PngImportOutcome.Failed(pickerFailure),
                adapter(content, InternalPickerResult.Failed(pickerFailure)).pick(),
            )
            assertEquals(0, content.openInputCalls)
        }

    @Test
    fun `source read failure is a failure of its transport phase`() =
        runBlocking {
            val content =
                MemoryProjectContent(
                    behavior =
                        ProjectContentBehavior(inputFactory = {
                            object : InputStream() {
                                override fun read(): Int = throw IOException("test read")
                            }
                        }),
                )
            assertEquals(
                PngImportOutcome.Failed(ProjectStorageFailure.IoFailure(ProjectTransportPhase.SOURCE_READ)),
                adapter(content).pick(),
            )
        }

    @Test
    fun `known length beyond the probe is too many bytes without opening the input`() =
        runBlocking {
            val content = MemoryProjectContent(knownByteCount = PngImportLimits.MAX_PROBE_BYTE_COUNT + 1L)
            assertEquals(rejected(PngImportSourceRejection.TooManyBytes), adapter(content).pick())
            assertEquals(0, content.openInputCalls)
        }

    @Test
    fun `a valid PNG padded one byte past the limit is too many bytes before decoding`() =
        runBlocking {
            val valid =
                TestPngBuilder.image(TestPngBuilder.header(1, 1, 8, 6), scanlines(packed(8, 1, 2, 3, 4)))
            val padded = valid.copyOf(PngImportLimits.MAX_ENCODED_BYTE_COUNT + 1)
            assertEquals(rejected(PngImportSourceRejection.TooManyBytes), pick(padded))
            assertTrue(pick(valid) is PngImportOutcome.Picked)
        }

    @Test
    fun `a side above 1024 is too many pixels`() =
        runBlocking {
            val bytes =
                TestPngBuilder.image(TestPngBuilder.header(1025, 1, 8, 0), scanlines(packed(8, *IntArray(1025))))
            assertEquals(rejected(PngImportSourceRejection.TooManyPixels), pick(bytes))
        }

    @Test
    fun `sixteen bit, non PNG and empty bytes are unsupported`() =
        runBlocking {
            val sixteenBit =
                TestPngBuilder.image(TestPngBuilder.header(1, 1, 16, 6), scanlines(ByteArray(8)))
            val jpegStart = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 0x10)
            val expected = rejected(PngImportSourceRejection.Unsupported)
            assertEquals(expected, pick(sixteenBit))
            assertEquals(expected, pick(jpegStart))
            assertEquals(expected, pick(ByteArray(0)))
        }

    private suspend fun pick(bytes: ByteArray): PngImportOutcome = adapter(MemoryProjectContent(bytes)).pick()

    private fun adapter(
        content: ProjectContentAccess,
        pickerResult: InternalPickerResult = InternalPickerResult.Selected(TestLocation),
    ): PngImportPort = AndroidPngImportAdapter.create(content, FixedProjectPicker(pickerResult), dispatchers.inline)

    private fun rejected(reason: PngImportSourceRejection): PngImportOutcome = PngImportOutcome.Rejected(reason)
}
