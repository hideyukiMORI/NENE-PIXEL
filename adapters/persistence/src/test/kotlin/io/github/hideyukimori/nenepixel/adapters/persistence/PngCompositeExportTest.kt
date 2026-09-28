package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal class PngCompositeExportTest {
    private val size: CanvasSize = CanvasSize.create(value(CanvasWidth.create(3)), value(CanvasHeight.create(1)))

    @Test
    fun `layered PNG carries the hand computed composite and transparent empty pixels`() {
        val bytes = PngEncoder.encode(document()).copyBytes()
        val image = checkNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        assertEquals(3, image.width)
        assertEquals(1, image.height)
        // ADR 0030 source-over of 0xa0c0e080 onto 0x204060ff:
        // weights 128*255 = 32640 and 255*127 = 32385, alpha (65025 + 127) / 255 = 255,
        // red (2 * (160*32640 + 32*32385) + 65025) / 130050 = 96, green 128, blue 160.
        val expectedRgba = intArrayOf(0x6080a0ff, 0x204060ff, 0)
        expectedRgba.forEachIndexed { pixel, rgba ->
            assertEquals((rgba ushr 8) or (rgba shl 24), image.getRGB(pixel, 0), "pixel $pixel")
        }
    }

    private fun document(): DocumentState {
        val layers = listOf(layer(1, listOf(0, 0, null)), layer(2, listOf(1, null, null)))
        val colors = listOf(0x204060ff, 0xa0c0e080.toInt()).map(PixelColor::fromPackedRgba8888)
        val definition = value(PaletteDefinition.create(value(Palette.create(colors)), value(PaletteIndex.create(0))))
        val id = value(DocumentId.create("0".repeat(32)))
        return value(DocumentState.createLayered(id, value(Revision.create(0)), definition, layers))
    }

    private fun layer(
        id: Int,
        cells: List<Int?>,
    ): Layer {
        val indices = ByteArray(cells.size) { (cells[it] ?: 0).toByte() }
        var coverage = 0
        cells.forEachIndexed { pixel, cell -> if (cell != null) coverage = coverage or (1 shl pixel) }
        val snapshot = value(PixelSnapshot.createPackedCells(size, indices, byteArrayOf(coverage.toByte())))
        return Layer.create(value(LayerId.create(id)), LayerName.empty, LayerVisibility.Visible, snapshot)
    }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected fixture rejection: ${result.rejection}")
        }
}
