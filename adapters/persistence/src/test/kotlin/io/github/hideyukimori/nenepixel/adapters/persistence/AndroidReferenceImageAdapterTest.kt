package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageSourceRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream

internal class AndroidReferenceImageAdapterTest {
    private val dispatchers = TestCoroutineDispatchers()

    @Test
    fun `picker receives one reference image open request`() =
        runBlocking {
            val content = MemoryProjectContent(SOURCE_BYTES)
            val requests = mutableListOf<DocumentOpenRequest>()
            val picker =
                object : ProjectPickerAccess {
                    override suspend fun createDocument(request: DocumentCreationRequest): InternalPickerResult =
                        error("Picking a reference image must not create an output")

                    override suspend fun openDocument(request: DocumentOpenRequest): InternalPickerResult {
                        requests += request
                        return InternalPickerResult.Selected(TestLocation)
                    }
                }
            AndroidReferenceImageAdapter.create(content, picker, dispatchers.inline, RecordingDecoder()).pick()
            assertEquals(listOf(DocumentOpenRequest(DocumentOutputFormat.REFERENCE_IMAGE)), requests)
            assertEquals(0, content.openOutputCalls)
        }

    @Test
    fun `picker cancellation and failure open no input and decode nothing`() =
        runBlocking {
            val content = MemoryProjectContent(SOURCE_BYTES)
            val decoder = RecordingDecoder()
            val pickerFailure = ProjectStorageFailure.PermissionDenied(ProjectTransportPhase.PICKER_RESULT)
            assertEquals(
                ReferenceImageOutcome.Cancelled,
                adapter(content, decoder, InternalPickerResult.Cancelled).pick(),
            )
            assertEquals(
                ReferenceImageOutcome.Failed(pickerFailure),
                adapter(content, decoder, InternalPickerResult.Failed(pickerFailure)).pick(),
            )
            assertEquals(0, content.openInputCalls)
            assertEquals(0, decoder.calls.size)
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
            val decoder = RecordingDecoder()
            assertEquals(
                ReferenceImageOutcome.Failed(ProjectStorageFailure.IoFailure(ProjectTransportPhase.SOURCE_READ)),
                adapter(content, decoder).pick(),
            )
            assertEquals(0, decoder.calls.size)
        }

    @Test
    fun `known length beyond the probe is too many bytes without opening the input`() =
        runBlocking {
            val content = MemoryProjectContent(knownByteCount = ReferenceImageLimits.MAX_PROBE_BYTE_COUNT + 1L)
            val decoder = RecordingDecoder()
            assertEquals(rejected(ReferenceImageSourceRejection.TooManyBytes), adapter(content, decoder).pick())
            assertEquals(0, content.openInputCalls)
            assertEquals(0, decoder.calls.size)
        }

    @Test
    fun `unknown length stream one byte past the limit is too many bytes`() =
        runBlocking {
            val content =
                MemoryProjectContent(inputFactory = {
                    RepeatingInputStream(ReferenceImageLimits.MAX_ENCODED_BYTE_COUNT + 1L)
                })
            val decoder = RecordingDecoder()
            assertEquals(rejected(ReferenceImageSourceRejection.TooManyBytes), adapter(content, decoder).pick())
            assertEquals(0, decoder.calls.size)
        }

    @Test
    fun `decoder rejections map to source rejections`() =
        runBlocking {
            val content = MemoryProjectContent(SOURCE_BYTES)
            assertEquals(
                rejected(ReferenceImageSourceRejection.TooManyPixels),
                adapter(content, RecordingDecoder(ReferenceImageDecodeResult.TooManyPixels)).pick(),
            )
            assertEquals(
                rejected(ReferenceImageSourceRejection.Unsupported),
                adapter(content, RecordingDecoder(ReferenceImageDecodeResult.Unsupported)).pick(),
            )
        }

    @Test
    fun `decoded raster that breaks the image rules is unsupported`() =
        runBlocking {
            val content = MemoryProjectContent(SOURCE_BYTES)
            val overSide = ReferenceImage.MAX_SIDE + 1
            val mismatched = ReferenceImageDecodeResult.Decoded(2, 2, IntArray(3))
            val tooWide = ReferenceImageDecodeResult.Decoded(overSide, 1, IntArray(overSide))
            val expected = rejected(ReferenceImageSourceRejection.Unsupported)
            assertEquals(expected, adapter(content, RecordingDecoder(mismatched)).pick())
            assertEquals(expected, adapter(content, RecordingDecoder(tooWide)).pick())
        }

    @Test
    fun `decoded raster becomes the picked image and the input is closed`() =
        runBlocking {
            val content = MemoryProjectContent(SOURCE_BYTES)
            val pixels = intArrayOf(0xff0000ff.toInt(), 0x00ff00ff, 0x0000ffff, 0x00000000, 0x12345678, -1)
            val decoder = RecordingDecoder(ReferenceImageDecodeResult.Decoded(3, 2, pixels))
            val outcome = adapter(content, decoder).pick()
            val image = (outcome as ReferenceImageOutcome.Picked).image
            assertEquals(3, image.width)
            assertEquals(2, image.height)
            assertArrayEquals(pixels, image.copyPackedRgba8888())
            assertArrayEquals(SOURCE_BYTES, decoder.calls.single())
            assertTrue(content.inputClosed)
            assertEquals(1, content.openInputCalls)
        }

    private fun adapter(
        content: ProjectContentAccess,
        decoder: ReferenceImageDecoder,
        pickerResult: InternalPickerResult = InternalPickerResult.Selected(TestLocation),
    ): ReferenceImagePort =
        AndroidReferenceImageAdapter.create(content, FixedProjectPicker(pickerResult), dispatchers.inline, decoder)

    private fun rejected(reason: ReferenceImageSourceRejection): ReferenceImageOutcome =
        ReferenceImageOutcome.Rejected(reason)

    private class RecordingDecoder(
        private val result: ReferenceImageDecodeResult = ReferenceImageDecodeResult.Unsupported,
    ) : ReferenceImageDecoder {
        val calls: MutableList<ByteArray> = mutableListOf()

        override fun decode(encoded: ByteArray): ReferenceImageDecodeResult {
            calls += encoded
            return result
        }
    }

    private class RepeatingInputStream(
        private var remaining: Long,
    ) : InputStream() {
        override fun read(): Int =
            if (remaining <= 0L) {
                -1
            } else {
                remaining -= 1L
                0
            }

        override fun read(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): Int =
            if (remaining <= 0L) {
                -1
            } else {
                val count = minOf(length.toLong(), remaining).toInt()
                remaining -= count
                count
            }
    }

    private companion object {
        val SOURCE_BYTES: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
    }
}
