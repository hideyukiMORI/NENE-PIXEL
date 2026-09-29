package io.github.hideyukimori.nenepixel.core.application.render

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
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.compositeLayers
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DocumentCompositeTest {
    private val size: CanvasSize = CanvasSize.create(value(CanvasWidth.create(3)), value(CanvasHeight.create(1)))

    @Test
    fun `render matches the pixel engine composite for visible hidden and empty cells`() {
        val document = document()
        val image = DocumentComposite.render(document)
        val expected = raster(compositeLayers(document.size, document.layers, document.definition))
        assertEquals(document.size, image.size)
        assertArrayEquals(expected, image.copyPackedRgba8888())
        // Pixel 0: 0xa0c0e080 over 0x204060ff; pixel 1: bottom only; pixel 2: Empty in every visible layer.
        assertArrayEquals(intArrayOf(0x6080a0ff, 0x204060ff, 0), image.copyPackedRgba8888())
    }

    @Test
    fun `image hands out defensive copies`() {
        val image = DocumentComposite.render(document())
        image.copyPackedRgba8888().fill(1)
        assertArrayEquals(intArrayOf(0x6080a0ff, 0x204060ff, 0), image.copyPackedRgba8888())
    }

    @Test
    fun `map transforms every pixel once in row-major order`() {
        val image = DocumentComposite.render(document())
        val seen = mutableListOf<Int>()
        val mapped = image.mapPackedRgba8888 { packed -> packed.also(seen::add) xor MASK }
        assertEquals(listOf(0x6080a0ff, 0x204060ff, 0), seen)
        assertArrayEquals(intArrayOf(0x6080a0ff xor MASK, 0x204060ff xor MASK, MASK), mapped)
    }

    @Test
    fun `mapped arrays are defensive`() {
        val image = DocumentComposite.render(document())
        image.mapPackedRgba8888 { it }.fill(1)
        assertArrayEquals(intArrayOf(0x6080a0ff, 0x204060ff, 0), image.mapPackedRgba8888 { it })
        assertArrayEquals(intArrayOf(0x6080a0ff, 0x204060ff, 0), image.copyPackedRgba8888())
    }

    @Test
    fun `draft definition recolors the document layers`() {
        val document = document()
        val draft = definition(0x000000ff, 0xffffff00.toInt(), 0x123456ff)
        val result = DocumentComposite.render(document, draft)
        val expected = raster(compositeLayers(document.size, document.layers, draft))
        val rendered = result as DocumentCompositeRenderResult.Rendered
        assertArrayEquals(expected, rendered.image.copyPackedRgba8888())
        assertArrayEquals(intArrayOf(0x000000ff, 0x000000ff, 0), expected)
    }

    @Test
    fun `draft definition without a used index is reported as outside the palette`() {
        val draft = definition(0x000000ff, 0xffffffff.toInt())
        assertEquals(DocumentCompositeRenderResult.IndexOutsidePalette, DocumentComposite.render(document(), draft))
    }

    private fun document(): DocumentState {
        val layers =
            listOf(
                layer(1, LayerVisibility.Visible, listOf(0, 0, null)),
                layer(2, LayerVisibility.Hidden, listOf(2, 2, 2)),
                layer(3, LayerVisibility.Visible, listOf(1, null, null)),
            )
        val definition = definition(0x204060ff, 0xa0c0e080.toInt(), 0xff0000ff.toInt())
        val id = value(DocumentId.create("0".repeat(32)))
        return value(DocumentState.createLayered(id, value(Revision.create(0)), definition, layers))
    }

    private fun layer(
        id: Int,
        visibility: LayerVisibility,
        cells: List<Int?>,
    ): Layer {
        val indices = ByteArray(cells.size) { (cells[it] ?: 0).toByte() }
        var coverage = 0
        cells.forEachIndexed { pixel, cell -> if (cell != null) coverage = coverage or (1 shl pixel) }
        val snapshot = value(PixelSnapshot.createPackedCells(size, indices, byteArrayOf(coverage.toByte())))
        return Layer.create(value(LayerId.create(id)), LayerName.empty, visibility, snapshot)
    }

    private fun definition(vararg rgba: Int): PaletteDefinition =
        value(
            PaletteDefinition.create(
                value(Palette.create(rgba.map(PixelColor::fromPackedRgba8888))),
                value(PaletteIndex.create(0)),
            ),
        )

    private fun raster(result: CompositeResult): IntArray =
        when (result) {
            is CompositeResult.Composited -> result.raster.copyPackedRgba8888()
            is CompositeResult.Rejected -> error("Unexpected composite rejection: ${result.rejection}")
        }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected fixture rejection: ${result.rejection}")
        }

    private companion object {
        const val MASK: Int = 0x0F0F0F0F
    }
}
