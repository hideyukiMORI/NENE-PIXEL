package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ProjectFormatV1CodecGoldenTest {
    @Test
    fun `minimal golden is exact and hand checkable`() {
        val golden = ProjectFormatTestValues.golden("minimal-v1.hex")
        val encoded = ProjectFormatCodec.encodeLegacySource(ProjectFormatTestValues.minimalLegacySource())

        assertEquals(ProjectFormatV1Layout.MIN_FILE_BYTE_COUNT, golden.size)
        assertArrayEquals(golden, encoded.copyBytes())
        assertEquals(ProjectFormatTestValues.minimalLegacySource(), legacy(ProjectFormatTestValues.carrier(golden)))
        val expectedChecksum =
            byteArrayOf(0xee.toByte(), 0xec.toByte(), 0xf0.toByte(), 0xa6.toByte())
        assertArrayEquals(expectedChecksum, golden.takeLast(Int.SIZE_BYTES).toByteArray())
    }

    @Test
    fun `rectangular golden preserves row order hidden RGB and maximum revision`() {
        val golden = ProjectFormatTestValues.golden("rectangular-hidden-rgb-v1.hex")
        val encoded = ProjectFormatCodec.encodeLegacySource(ProjectFormatTestValues.rectangularLegacySource())
        val decoded = legacy(ProjectFormatTestValues.carrier(golden))

        assertArrayEquals(golden, encoded.copyBytes())
        assertEquals(ProjectFormatTestValues.rectangularLegacySource(), decoded)
        val expectedPixels = ProjectFormatTestValues.rectangularLegacySource().copyPackedRgba8888()
        assertArrayEquals(expectedPixels, decoded.copyPackedRgba8888())
    }

    @Test
    fun `identical documents encode deterministically`() {
        val first = ProjectFormatCodec.encodeLegacySource(ProjectFormatTestValues.rectangularLegacySource())
        val second = ProjectFormatCodec.encodeLegacySource(ProjectFormatTestValues.rectangularLegacySource())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `generated maximum boundary round trips exactly`() {
        val source = ProjectFormatTestValues.maximumLegacySource()
        val encoded = ProjectFormatCodec.encodeLegacySource(source)
        val decoded = legacy(encoded)

        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, encoded.byteCount)
        assertEquals(source, decoded)
        assertTrue(encoded.copyBytes().size <= ProjectFormatBytes.MAX_FILE_BYTE_COUNT)
    }

    private fun legacy(source: ProjectFormatBytes) =
        when (val result = ProjectFormatCodec.decode(source)) {
            is ProjectFormatResult.Accepted -> (result.value as DocumentImportSource.Legacy).source
            is ProjectFormatResult.Rejected -> error("Expected decoded document, got ${result.rejection}")
        }
}
