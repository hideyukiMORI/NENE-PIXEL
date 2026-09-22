package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_EDGE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.FULL_PALETTE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.applied
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.changedPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.remap
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.index
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class PaletteRemapApplicationScanTest {
    @Test
    fun `identity remap over a full palette raster reports no index changes`() {
        val full = palette(FULL_PALETTE)
        val identity = remap(full, full, List(FULL_PALETTE) { it })
        val raster = snapshot(canvas(CANVAS_EDGE, CANVAS_EDGE)) { position -> position % FULL_PALETTE }

        assertEquals(PaletteRemapApplicationResult.NoIndexChanges, applyPaletteRemap(raster, identity))
    }

    @Test
    fun `a changed slot that the raster never uses still reports no index changes`() {
        val source = palette(SPARSE_PALETTE)
        val target = palette(SPARSE_PALETTE)
        val movedSlot = remap(source, target, List(SPARSE_PALETTE) { if (it == MOVED_SLOT) MOVED_TARGET else it })
        val raster = snapshot(canvas(5, 4)) { position -> position % MOVED_SLOT }

        assertEquals(PaletteRemapApplicationResult.NoIndexChanges, applyPaletteRemap(raster, movedSlot))
    }

    @Test
    fun `a single pixel canvas keeps its exact patch`() {
        val small = palette(3)
        val tiny = canvas(1, 1)
        val raster = snapshot(tiny) { 1 }

        val patch = changedPatch(applyPaletteRemap(raster, remap(small, small, COLLAPSE_TO_LAST)))
        val changed = applied(patch.applyTo(raster))

        assertEquals(1, patch.changeCount)
        assertEquals(region(tiny, position(0, 0), tiny), patch.affectedRegion)
        assertEquals(listOf(2), changed.copyPackedIndices().map(Byte::toInt))
        assertEquals(raster, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `a change at the first or the last raster position keeps its own single pixel region`() {
        val small = palette(3)
        val board = canvas(4, 4)
        val collapse = remap(small, small, COLLAPSE_TO_LAST)
        val first = changedPatch(applyPaletteRemap(snapshot(board) { if (it == 0) 1 else 0 }, collapse))
        val last = changedPatch(applyPaletteRemap(snapshot(board) { if (it == LAST_POSITION) 1 else 0 }, collapse))

        assertEquals(1, first.changeCount)
        assertEquals(region(board, position(0, 0), canvas(1, 1)), first.affectedRegion)
        assertEquals(1, last.changeCount)
        assertEquals(region(board, position(3, 3), canvas(1, 1)), last.affectedRegion)
    }

    @Test
    fun `a raster whose maximum index fills the last palette slot is accepted`() {
        val small = palette(3)
        val raster = snapshot(canvas(2, 1)) { position -> position + 1 }

        val patch = changedPatch(applyPaletteRemap(raster, remap(small, small, COLLAPSE_TO_LAST)))

        assertEquals(index(2), raster.maximumIndex)
        assertEquals(1, patch.changeCount)
        assertEquals(listOf(2, 2), applied(patch.applyTo(raster)).copyPackedIndices().map(Byte::toInt))
    }

    @Test
    fun `an index outside the remap source is rejected at its first position even for an identity map`() {
        val small = palette(2)
        val identity = remap(small, small, listOf(0, 1))
        val raster = snapshot(canvas(3, 2)) { position -> if (position < 4) 1 else 2 }

        val result =
            assertInstanceOf(
                PaletteRemapApplicationResult.Rejected::class.java,
                applyPaletteRemap(raster, identity),
            )
        val outside =
            assertInstanceOf(
                PaletteRemapApplicationRejection.SourceIndexOutsidePalette::class.java,
                result.rejection,
            )

        assertEquals(position(1, 1), outside.position)
        assertEquals(index(2), outside.index)
        assertEquals(2, outside.entryCount)
    }

    private companion object {
        const val SPARSE_PALETTE: Int = 8
        const val MOVED_SLOT: Int = 5
        const val MOVED_TARGET: Int = 7
        const val LAST_POSITION: Int = 15
        val COLLAPSE_TO_LAST: List<Int> = listOf(0, 2, 2)
    }
}
