package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.composited
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.filledLayer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layerId
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.rejection
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.value
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompositeLayersTest {
    private val colors = palette(0x00000000, 0xFF000080, 0x0A141E00, 0x102030FF, 0x7F7F7F01)

    @Test
    fun `single visible layer matches palette RGBA for every pixel`() {
        val size = canvas(5, 2)
        val cells = listOf(0, 1, 2, 3, 4, 4, 3, 2, 1, 0)
        val entries = colors.palette.entries()

        val rgba = composited(compositeLayers(size, listOf(layer(1, size, cells)), colors))

        assertArrayEquals(cells.map { entries[it].color.toPackedRgba8888() }.toIntArray(), rgba)
    }

    @Test
    fun `raster copies are defensive`() {
        val size = canvas(1, 1)
        val raster =
            (compositeLayers(size, listOf(layer(1, size, listOf(3))), colors) as CompositeResult.Composited).raster

        raster.copyPackedRgba8888()[0] = 0

        assertArrayEquals(intArrayOf(0x102030FF), raster.copyPackedRgba8888())
        assertEquals(size, raster.size)
    }

    @Test
    fun `no layers is rejected`() {
        assertEquals(CompositeRejection.NoLayers, rejection(compositeLayers(canvas(1, 1), emptyList(), colors)))
    }

    @Test
    fun `layer size mismatch is rejected even for hidden layers`() {
        val size = canvas(1, 1)
        val wide = canvas(2, 1)
        val layers = listOf(layer(1, size, listOf(0)), layer(2, wide, listOf(0, 0), LayerVisibility.Hidden))

        assertEquals(
            CompositeRejection.LayerSizeMismatch(layerId(2), size, wide),
            rejection(compositeLayers(size, layers, colors)),
        )
    }

    @Test
    fun `index outside palette is rejected even for hidden layers`() {
        val size = canvas(2, 1)
        val layers = listOf(layer(1, size, listOf(0, 1)), layer(2, size, listOf(null, 5), LayerVisibility.Hidden))

        assertEquals(
            CompositeRejection.IndexOutsidePalette(layerId(2), value(PaletteIndex.create(5))),
            rejection(compositeLayers(size, layers, colors)),
        )
    }

    @Test
    fun `sixteen fully covered layers at the canvas limit composite`() {
        val size = canvas(256, 256)
        val layers = List(16) { position -> filledLayer(position + 1, size, if (position == 15) 3 else 1) }

        val rgba = composited(compositeLayers(size, layers, colors))

        assertEquals(65_536, rgba.size)
        assertEquals(0x102030FF, rgba[40_000])
    }

    @Test
    fun `stacked saturated partial alpha does not overflow`() {
        // Alternating (255,255,255,254) and (255,255,255,1): alpha goes 254, 254, 255 and stays 255.
        val size = canvas(1, 1)
        val white = palette(0x00000000, 0xFFFFFFFE, 0xFFFFFF01)
        val layers = List(16) { position -> layer(position + 1, size, listOf(1 + position % 2)) }

        assertArrayEquals(intArrayOf(-1), composited(compositeLayers(size, layers, white)))
    }
}
