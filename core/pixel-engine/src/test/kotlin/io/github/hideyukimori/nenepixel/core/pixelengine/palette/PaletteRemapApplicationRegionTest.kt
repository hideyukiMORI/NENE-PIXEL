package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_EDGE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_PIXELS
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.FULL_PALETTE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.applied
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.changedPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.remap
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.snapshot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PaletteRemapApplicationRegionTest {
    private val collapseToLast: PaletteRemap = palette(3).let { remap(it, it, listOf(0, 2, 2)) }

    @Test
    fun `many to one remap of every pixel keeps the affected region at the whole canvas`() {
        val source = palette(FULL_PALETTE)
        val target = palette(2)
        val full = canvas(CANVAS_EDGE, CANVAS_EDGE)
        val raster = snapshot(full) { position -> position % (FULL_PALETTE - 2) + 2 }

        val patch = changedPatch(applyPaletteRemap(raster, remap(source, target, List(FULL_PALETTE) { 0 })))
        val changed = applied(patch.applyTo(raster))

        assertEquals(CANVAS_PIXELS, patch.changeCount)
        assertEquals(region(full, position(0, 0), full), patch.affectedRegion)
        assertArrayEquals(ByteArray(CANVAS_PIXELS), changed.copyPackedIndices())
        assertEquals(raster, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `scattered changes keep the exact bounds of their own positions`() {
        val small = canvas(3, 3)
        val raster = snapshot(small) { position -> if (position in setOf(1, 2, 4)) 1 else 0 }

        val patch = changedPatch(applyPaletteRemap(raster, collapseToLast))
        val changed = applied(patch.applyTo(raster))

        assertEquals(3, patch.changeCount)
        assertEquals(region(small, position(1, 0), canvas(2, 2)), patch.affectedRegion)
        assertEquals(listOf(0, 2, 2, 0, 2, 0, 0, 0, 0), changed.copyPackedIndices().map(Byte::toInt))
        assertEquals(raster, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `a contiguous run inside one row keeps that row segment`() {
        val board = canvas(4, 4)

        assertEquals(region(board, position(1, 1), canvas(3, 1)), runRegion(board, 5..7))
    }

    @Test
    fun `a contiguous run across two rows widens to both full rows`() {
        val board = canvas(4, 4)

        assertEquals(region(board, position(0, 0), canvas(4, 2)), runRegion(board, 3..5))
    }

    private fun runRegion(
        board: CanvasSize,
        run: IntRange,
    ): PixelRegion {
        val raster = snapshot(board) { position -> if (position in run) 1 else 0 }
        val patch = changedPatch(applyPaletteRemap(raster, collapseToLast))
        assertEquals(run.count(), patch.changeCount)
        return patch.affectedRegion
    }
}
