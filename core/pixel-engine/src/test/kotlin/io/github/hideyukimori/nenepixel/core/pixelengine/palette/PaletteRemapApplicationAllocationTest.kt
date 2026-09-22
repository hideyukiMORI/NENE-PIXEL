package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_EDGE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_PIXELS
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.FULL_PALETTE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.allocatedBytes
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.remap
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.snapshot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PaletteRemapApplicationAllocationTest {
    @Test
    fun `identity remap allocates far less than one raster copy`() {
        val full = palette(FULL_PALETTE)
        val identity = remap(full, full, List(FULL_PALETTE) { it })
        val raster = snapshot(canvas(CANVAS_EDGE, CANVAS_EDGE)) { position -> position % FULL_PALETTE }
        repeat(WARMUPS) { applyPaletteRemap(raster, identity) }

        val allocated = allocatedBytes { applyPaletteRemap(raster, identity) }

        assertTrue(
            allocated < CANVAS_PIXELS / 2,
            "Identity remap allocated $allocated bytes; one raster copy alone costs $CANVAS_PIXELS bytes.",
        )
    }

    @Test
    fun `many to one remap allocates only its change payload`() {
        val source = palette(FULL_PALETTE)
        val target = palette(2)
        val raster = snapshot(canvas(CANVAS_EDGE, CANVAS_EDGE)) { position -> position % (FULL_PALETTE - 2) + 2 }
        val collapse = remap(source, target, List(FULL_PALETTE) { 0 })
        repeat(WARMUPS) { applyPaletteRemap(raster, collapse) }

        val allocated = allocatedBytes { applyPaletteRemap(raster, collapse) }
        val ceiling = CANVAS_PIXELS * MAXIMUM_BYTES_PER_CHANGED_PIXEL

        assertTrue(
            allocated <= ceiling,
            "Dense many-to-one remap allocated $allocated bytes above its $ceiling byte ceiling.",
        )
    }

    private companion object {
        const val WARMUPS: Int = 8
        const val MAXIMUM_BYTES_PER_CHANGED_PIXEL: Long = 20L
    }
}
