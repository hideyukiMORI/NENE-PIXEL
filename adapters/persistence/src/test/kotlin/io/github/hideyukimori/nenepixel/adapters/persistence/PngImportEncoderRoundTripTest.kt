package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.TestPngDecoding.assertDecoded
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Test

/** The import decoder reads the output of the export [PngEncoder] back into the same pixels. */
internal class PngImportEncoderRoundTripTest {
    @Test
    fun `minimal export decodes to its one pixel`() {
        val bytes = PngEncoder.encode(PersistenceTestValues.minimalDocument).copyBytes()
        assertDecoded(bytes, 1, 1, intArrayOf(0x11223344))
    }

    @Test
    fun `rectangle export keeps hidden RGB and low alpha exactly`() {
        val colors =
            intArrayOf(0x10203000, 0xabcdef01.toInt(), 0x1122337f, 0xdeadbefe.toInt(), 0x010203ff, 0)
        val bytes = PngEncoder.encode(rectangle(colors)).copyBytes()
        assertDecoded(
            bytes,
            3,
            2,
            intArrayOf(0x10203000, 0xabcdef01.toInt(), 0x1122337f, 0xdeadbefe.toInt(), 0x010203ff, 0),
        )
    }

    private fun rectangle(colors: IntArray): DocumentState {
        val size = CanvasSize.create(created(CanvasWidth.create(3)), created(CanvasHeight.create(2)))
        val palette = created(Palette.create(colors.map(PixelColor::fromPackedRgba8888)))
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(0))))
        val snapshot = created(PixelSnapshot.createPackedIndices(size, byteArrayOf(0, 1, 2, 3, 4, 5)))
        val id = created(DocumentId.create("0".repeat(32)))
        return created(DocumentState.createSingleLayer(id, created(Revision.create(0)), definition, snapshot))
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid PNG fixture")
        }
}
