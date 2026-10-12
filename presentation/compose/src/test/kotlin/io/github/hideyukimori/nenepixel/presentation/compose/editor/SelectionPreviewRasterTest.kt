package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.render.SelectionPreviewComposite
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionPreviewRasterTest {
    @Test
    fun `distant rectangles restore and transfer only bounded footprints with no gap scan`() {
        val size = canvas(256, 256)
        val document = fixture(size).initialDocument
        val source = PixelRegion.create(size, position(0, 0), canvas(1, 1)).requiredValue()
        val composite = SelectionPreviewComposite.prepare(document, document.layers.single().id, source)
        val base = IntArray(65_536) { it }
        val buffer = base.copyOf()
        val raster = SelectionPreviewRaster(256, base, buffer)
        raster.paint(composite.render(transfer(size, source, 255)))
        val first = mutableListOf<PixelRegion>()
        raster.transfer { _, region -> first.add(region) }
        assertEquals(2L, first.sumOf { it.size.pixelCount })
        raster.clear()
        assertEquals(2, raster.lastRestoredPixelCount)
        raster.paint(composite.render(transfer(size, source, 128)))
        val next = mutableListOf<PixelRegion>()
        raster.transfer { _, region -> next.add(region) }
        assertEquals(3L, next.sumOf { it.size.pixelCount })
        assertEquals(65_535, buffer[65_535], "Old destination restored exactly")
        assertEquals(base[30_000], buffer[30_000], "Bounding-box gap untouched")
        raster.clear()
        assertEquals(base.toList(), buffer.toList())
    }

    @Test
    fun `Move to Copy restores source instead of retaining a cleared ghost`() {
        val size = canvas(3, 1)
        val document = fixture(size).initialDocument
        val source = PixelRegion.create(size, position(0, 0), canvas(1, 1)).requiredValue()
        val composite = SelectionPreviewComposite.prepare(document, document.layers.single().id, source)
        val base = intArrayOf(RED, GREEN, BLUE)
        val buffer = base.copyOf()
        val raster = SelectionPreviewRaster(3, base, buffer)
        val move =
            PixelTransfer
                .create(
                    size,
                    source,
                    PixelPlacement.create(position(2, 0)),
                    PixelTransferMode.Move,
                ).requiredValue()
        val copy =
            PixelTransfer
                .create(
                    size,
                    source,
                    PixelPlacement.create(position(2, 0)),
                    PixelTransferMode.Copy,
                ).requiredValue()
        raster.paint(composite.render(move))
        raster.transfer { _, _ -> }
        raster.clear()
        raster.paint(composite.render(copy))
        assertEquals(RED, buffer[0])
        assertEquals(0, buffer[2])
    }

    @Test
    fun `quarter turn restores the old wide destination and transfers actual tall rectangles`() {
        val size = canvas(7, 4)
        val document = fixture(size).initialDocument
        val source = PixelRegion.create(size, position(0, 0), canvas(3, 2)).requiredValue()
        val composite = SelectionPreviewComposite.prepare(document, document.layers.single().id, source)
        val base = IntArray(28) { it + 1 }
        val buffer = base.copyOf()
        val raster = SelectionPreviewRaster(7, base, buffer)
        val placement = PixelPlacement.create(position(4, 0))
        val old = PixelTransfer.create(size, source, placement, PixelTransferMode.Copy).requiredValue()
        val turned =
            PixelTransfer
                .create(
                    size,
                    source,
                    PixelPlacement.create(position(4, 0), PixelOrientation.RotateClockwise),
                    PixelTransferMode.Copy,
                ).requiredValue()
        raster.paint(composite.render(old))
        raster.transfer { _, _ -> }
        raster.clear()
        assertEquals(6, raster.lastRestoredPixelCount)
        raster.paint(composite.render(turned))
        val regions = mutableListOf<PixelRegion>()
        raster.transfer { _, region -> regions.add(region) }
        assertEquals(listOf(old.destinationRegion, turned.destinationRegion), regions)
        assertEquals(12L, regions.sumOf { it.size.pixelCount })
        assertEquals(base[6], buffer[6], "Old wide right edge restored")
        assertEquals(base[13], buffer[13], "Old wide second row restored")
        assertEquals(0, buffer[18], "New tall third row painted")
        assertEquals(base[3], buffer[3], "Gap outside both rectangles untouched")
        raster.clear()
        assertEquals(6, raster.lastRestoredPixelCount)
        assertEquals(base.toList(), buffer.toList())
    }

    private fun transfer(
        size: CanvasSize,
        source: PixelRegion,
        origin: Int,
    ): PixelTransfer =
        PixelTransfer
            .create(
                size,
                source,
                PixelPlacement.create(position(origin, origin)),
                PixelTransferMode.Move,
            ).requiredValue()

    private companion object {
        const val RED: Int = 0xFFFF0000.toInt()
        const val GREEN: Int = 0xFF00FF00.toInt()
        const val BLUE: Int = 0xFF0000FF.toInt()
    }
}
