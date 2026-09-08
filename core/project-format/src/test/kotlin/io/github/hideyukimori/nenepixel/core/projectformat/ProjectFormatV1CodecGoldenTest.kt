package io.github.hideyukimori.nenepixel.core.projectformat

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ProjectFormatV1CodecGoldenTest {
    @Test
    fun `minimal golden is exact and hand checkable`() {
        val golden = ProjectFormatTestValues.golden("minimal-v1.hex")
        val encoded = ProjectFormatV1Codec.encode(ProjectFormatTestValues.minimalDocument)

        assertEquals(ProjectFormatV1Layout.MIN_FILE_BYTE_COUNT, golden.size)
        assertArrayEquals(golden, encoded.copyBytes())
        assertEquals(ProjectFormatTestValues.minimalDocument, decoded(ProjectFormatTestValues.carrier(golden)))
        val expectedChecksum =
            byteArrayOf(0xee.toByte(), 0xec.toByte(), 0xf0.toByte(), 0xa6.toByte())
        assertArrayEquals(expectedChecksum, golden.takeLast(Int.SIZE_BYTES).toByteArray())
    }

    @Test
    fun `rectangular golden preserves row order hidden RGB and maximum revision`() {
        val golden = ProjectFormatTestValues.golden("rectangular-hidden-rgb-v1.hex")
        val encoded = ProjectFormatV1Codec.encode(ProjectFormatTestValues.rectangularDocument)
        val decoded = decoded(ProjectFormatTestValues.carrier(golden))

        assertArrayEquals(golden, encoded.copyBytes())
        assertEquals(ProjectFormatTestValues.rectangularDocument, decoded)
        val expectedPixels = ProjectFormatTestValues.rectangularDocument.snapshot.copyPackedRgba8888()
        assertArrayEquals(expectedPixels, decoded.snapshot.copyPackedRgba8888())
    }

    @Test
    fun `identical documents encode deterministically`() {
        val first = ProjectFormatV1Codec.encode(ProjectFormatTestValues.rectangularDocument)
        val second = ProjectFormatV1Codec.encode(ProjectFormatTestValues.rectangularDocument)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `generated maximum boundary round trips exactly`() {
        val document = ProjectFormatTestValues.maximumDocument()
        val encoded = ProjectFormatV1Codec.encode(document)
        val decoded = decoded(encoded)

        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, encoded.byteCount)
        assertEquals(document, decoded)
        assertTrue(encoded.copyBytes().size <= ProjectFormatBytes.MAX_FILE_BYTE_COUNT)
    }

    private fun decoded(source: ProjectFormatBytes) =
        when (val result = ProjectFormatV1Codec.decode(source)) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Expected decoded document, got ${result.rejection}")
        }
}
