package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.composited
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.filledLayer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.rejection
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PaletteCompositeTest {
    @Test
    fun `dependencies at supported limits retain one position per distinct covered slot`() {
        val size = canvas(256, 256)
        val layers = List(16) { filledLayer(it + 1, size, it) }
        val original = palette(*(0 until 16).map { (it.toLong() shl 8) or 255 }.toLongArray())
        val workspace = (preparePaletteComposite(size, layers, original) as PaletteCompositeResult.Prepared).composite
        val first = workspace.render(original)
        val changedBottom =
            palette(
                *(0 until 16)
                    .map {
                        if (it ==
                            0
                        ) {
                            0xFFFF00FF
                        } else {
                            (it.toLong() shl 8) or 255
                        }
                    }.toLongArray(),
            )

        assertEquals(65_536 * 16, workspace.dependencyPositionCount)
        assertSame(first, workspace.render(changedBottom), "An opaque upper layer keeps output unchanged")
        assertEquals(65_536, workspace.lastEvaluatedPixelCount)
    }

    @Test
    fun `updates equal the canonical composite across Empty hidden RGB and overlap`() {
        val size = canvas(4, 2)
        val layers =
            listOf(
                layer(1, size, listOf(0, 1, null, 2, 0, 1, null, null)),
                layer(2, size, listOf(null, 2, 1, 0, 0, null, null, null)),
                layer(3, size, List(8) { 3 }, LayerVisibility.Hidden),
            )
        val original = palette(0xAABBCC00, 0xFF000080, 0x0000FFFF, 0x00FF00FF)
        val workspace = (preparePaletteComposite(size, layers, original) as PaletteCompositeResult.Prepared).composite
        val first = workspace.render(original)
        val unchanged = first.copyPackedRgba8888()
        val tables =
            listOf(
                palette(0x01020300, 0xFF000080, 0x0000FFFF, 0x00FF00FF),
                palette(0x01020380, 0xFF000080, 0x0000FFFF, 0x00FF00FF),
                palette(0x01020380, 0x10203000, 0x0000FFFF, 0x00FF00FF),
                palette(0x01020380, 0x10203000, 0x0000FF00, 0x00FF00FF),
                palette(0x01020380, 0x01020380, 0x01020380, 0x01020380),
            )

        tables.forEach { table ->
            assertArrayEquals(
                composited(compositeLayers(size, layers, table)),
                workspace.render(table).copyPackedRgba8888(),
            )
        }
        assertArrayEquals(unchanged, first.copyPackedRgba8888(), "Previously published rasters remain immutable")
    }

    @Test
    fun `same table and unused or hidden-only slots reuse raster without pixel evaluation`() {
        val size = canvas(3, 1)
        val original = palette(0xFF0000FF, 0x00FF00FF, 0x0000FFFF)
        val layers = listOf(layer(1, size, listOf(0, null, 0)), layer(2, size, List(3) { 1 }, LayerVisibility.Hidden))
        val workspace = (preparePaletteComposite(size, layers, original) as PaletteCompositeResult.Prepared).composite
        val first = workspace.render(original)

        assertSame(first, workspace.render(palette(0xFF0000FF, 0x00FF00FF, 0x0000FFFF)))
        assertEquals(0, workspace.lastEvaluatedPixelCount)
        assertSame(first, workspace.render(palette(0xFF0000FF, 0xFFFFFFFF, 0xFFFFFF00)))
        assertEquals(0, workspace.lastEvaluatedPixelCount)
        assertEquals(2, workspace.dependencyPositionCount)
    }

    @Test
    fun `occluded changes remain dependencies and become visible when top alpha changes`() {
        val size = canvas(2, 1)
        val layers = listOf(layer(1, size, listOf(0, 0)), layer(2, size, listOf(1, 1)))
        val original = palette(0xFF0000FF, 0x0000FFFF)
        val workspace = (preparePaletteComposite(size, layers, original) as PaletteCompositeResult.Prepared).composite
        val first = workspace.render(original)

        assertSame(first, workspace.render(palette(0x00FF00FF, 0x0000FFFF)))
        assertEquals(2, workspace.lastEvaluatedPixelCount)
        val exposed = workspace.render(palette(0x00FF00FF, 0x12345600))
        assertNotSame(first, exposed)
        assertArrayEquals(intArrayOf(0x00FF00FF, 0x00FF00FF), exposed.copyPackedRgba8888())
    }

    @Test
    fun `overlapping changed slots evaluate each affected position once and dependencies deduplicate layers`() {
        val size = canvas(3, 1)
        val layers =
            listOf(
                layer(1, size, listOf(0, 0, null)),
                layer(2, size, listOf(0, 1, null)),
                layer(3, size, listOf(0, 1, 2)),
            )
        val original = palette(0xFF0000FF, 0x00FF0080, 0x0000FFFF)
        val workspace = (preparePaletteComposite(size, layers, original) as PaletteCompositeResult.Prepared).composite
        val next = palette(0x10203080, 0x20304080, 0x0000FFFF)

        assertArrayEquals(composited(compositeLayers(size, layers, next)), workspace.render(next).copyPackedRgba8888())
        assertEquals(2, workspace.lastEvaluatedPixelCount)
        assertEquals(4, workspace.dependencyPositionCount)
        workspace.render(original)
        assertEquals(2, workspace.lastEvaluatedPixelCount, "Union marks reset between updates")
    }

    @Test
    fun `preparation keeps canonical rejection and render requires an origin-sized table`() {
        val size = canvas(2, 1)
        val colors = palette(0xFF0000FF, 0x00FF00FF)
        val cases =
            listOf(
                emptyList(),
                listOf(layer(1, canvas(1, 1), listOf(0), LayerVisibility.Hidden)),
                listOf(layer(1, size, listOf(0, 2), LayerVisibility.Hidden)),
            )
        cases.forEach { layers ->
            val result = preparePaletteComposite(size, layers, colors) as PaletteCompositeResult.Rejected
            assertEquals(rejection(compositeLayers(size, layers, colors)), result.rejection)
        }
        val workspace =
            (
                preparePaletteComposite(
                    size,
                    listOf(layer(1, size, listOf(0, 1))),
                    colors,
                ) as PaletteCompositeResult.Prepared
            ).composite
        assertThrows(IllegalArgumentException::class.java) { workspace.render(palette(0, 1, 2)) }
    }
}
