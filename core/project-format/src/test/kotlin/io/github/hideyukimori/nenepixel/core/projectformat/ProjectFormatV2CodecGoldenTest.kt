package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ProjectFormatV2CodecGoldenTest {
    @Test
    fun `minimal v2 golden is exact and decodes current document`() {
        val golden = ProjectFormatTestValues.golden("minimal-v2.hex")
        val encoded = ProjectFormatCodec.encode(ProjectFormatTestValues.v2MinimalDocument)

        assertEquals(ProjectFormatV2Layout.MIN_FILE_BYTE_COUNT, golden.size)
        assertArrayEquals(golden, encoded.copyBytes())
        assertEquals(ProjectFormatTestValues.v2MinimalDocument, current(ProjectFormatTestValues.carrier(golden)))
    }

    @Test
    fun `rectangular v2 golden preserves duplicate slots and hidden RGB`() {
        val golden = ProjectFormatTestValues.golden("rectangular-duplicates-v2.hex")
        val encoded = ProjectFormatCodec.encode(ProjectFormatTestValues.v2RectangularDocument)

        assertEquals(63, golden.size)
        assertArrayEquals(golden, encoded.copyBytes())
        val current = current(ProjectFormatTestValues.carrier(golden))
        assertEquals(ProjectFormatTestValues.v2RectangularDocument, current)
        val entries = current.definition.palette.entries()
        assertEquals(0x11223300, entries[0].color.toPackedRgba8888())
        assertEquals(entries[1].color, entries[2].color)
        assertArrayEquals(byteArrayOf(0, 1, 2, 2, 1, 0), current.snapshot.copyPackedIndices())
    }

    @Test
    fun `indexed document writes v2 at the maximum layout`() {
        val encoded = ProjectFormatCodec.encode(ProjectFormatTestValues.maximumDocument())

        assertEquals(ProjectFormatV2Layout.MAX_FILE_BYTE_COUNT, encoded.byteCount)
    }

    private fun current(source: ProjectFormatBytes) =
        when (val result = ProjectFormatCodec.decode(source)) {
            is ProjectFormatResult.Accepted -> (result.value as DocumentImportSource.Current).document
            is ProjectFormatResult.Rejected -> error("Expected current document, got ${result.rejection}")
        }
}
