package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.palette
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompositeRasterTest {
    private val colors = palette(0x00000000, 0xFF000080, 0x0A141E00, 0x102030FF)

    @Test
    fun `map transforms every pixel once in row-major order`() {
        val raster = raster()
        val expected = raster.copyPackedRgba8888()
        val seen = mutableListOf<Int>()

        val mapped = raster.mapPackedRgba8888 { packed -> packed.also(seen::add) xor MASK }

        assertEquals(expected.toList(), seen)
        assertArrayEquals(expected.map { it xor MASK }.toIntArray(), mapped)
    }

    @Test
    fun `mapped arrays are defensive`() {
        val raster = raster()
        val expected = raster.copyPackedRgba8888()

        raster.mapPackedRgba8888 { it }.fill(1)

        assertArrayEquals(expected, raster.mapPackedRgba8888 { it })
        assertArrayEquals(expected, raster.copyPackedRgba8888())
    }

    private fun raster(): CompositeRaster {
        val size = canvas(3, 2)
        val result = compositeLayers(size, listOf(layer(1, size, listOf(0, 1, 2, 3, null, 1))), colors)
        return (result as CompositeResult.Composited).raster
    }

    private companion object {
        const val MASK: Int = 0x0F0F0F0F
    }
}
