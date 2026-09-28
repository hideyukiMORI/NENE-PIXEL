package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.composited
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.palette
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class CompositeLayersGoldenTest {
    private val pixel = canvas(1, 1)

    private val colors =
        palette(
            0x0A141E00, // 0: hidden RGB (10,20,30), alpha 0
            0xC8643280, // 1: (200,100,50,128)
            0x010203FF, // 2: opaque (1,2,3)
            0x09090900, // 3: alpha 0 (9,9,9)
            0x6400FF80, // 4: Sa=128 (100,0,255)
            0xC832FF80, // 5: Da=128 (200,50,255)
            0x32404600, // 6: Da=0 (50,64,70)
            0x7B2D4364, // 7: Sa=100 (123,45,67)
            0x090909FF, // 8: opaque (9,9,9)
        )

    private fun stack(cells: List<Int?>): IntArray =
        composited(
            compositeLayers(pixel, cells.mapIndexed { i, cell -> layer(i + 1, pixel, listOf(cell)) }, colors),
        )

    @Test
    fun `first contribution copies an alpha zero color with hidden RGB`() {
        assertArrayEquals(intArrayOf(0x0A141E00), stack(listOf(null, 0, null)))
    }

    @Test
    fun `first contribution copies a partial color exactly`() {
        assertArrayEquals(intArrayOf(0xC8643280.toInt()), stack(listOf(1)))
    }

    @Test
    fun `opaque color replaces the result`() {
        assertArrayEquals(intArrayOf(0x010203FF), stack(listOf(1, 2)))
    }

    @Test
    fun `alpha zero color over a result leaves it unchanged`() {
        assertArrayEquals(intArrayOf(0xC8643280.toInt()), stack(listOf(1, 3, null)))
    }

    @Test
    fun `partial over partial rounds to the golden vector`() {
        assertArrayEquals(intArrayOf(0x8511FFC0.toInt()), stack(listOf(5, 4)))
    }

    @Test
    fun `partial over an alpha zero result keeps the source color`() {
        assertArrayEquals(intArrayOf(0x7B2D4364), stack(listOf(6, 7)))
    }

    @Test
    fun `all empty cells composite to transparent black`() {
        assertArrayEquals(intArrayOf(0), stack(listOf(null, null)))
    }

    @Test
    fun `hidden layers do not contribute`() {
        val layers =
            listOf(
                layer(1, pixel, listOf(2)),
                layer(2, pixel, listOf(8), LayerVisibility.Hidden),
            )

        assertArrayEquals(intArrayOf(0x010203FF), composited(compositeLayers(pixel, layers, colors)))
    }
}
