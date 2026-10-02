package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HALF_RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HIDDEN_CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.raster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RasterImportColorCountTest {
    @Test
    fun `transparent pixels are not counted`() {
        assertEquals(1, RasterImportPlanner.colorCount(raster(3, 1, CLEAR, HIDDEN_CLEAR, RED)))
    }

    @Test
    fun `colours that differ only in alpha are counted apart`() {
        assertEquals(2, RasterImportPlanner.colorCount(raster(3, 1, RED, HALF_RED, RED)))
    }

    @Test
    fun `one repeated colour counts once`() {
        assertEquals(1, RasterImportPlanner.colorCount(raster(2, 2, RED, RED, RED, RED)))
    }

    @Test
    fun `a fully transparent raster has no colour`() {
        assertEquals(0, RasterImportPlanner.colorCount(raster(2, 1, CLEAR, HIDDEN_CLEAR)))
    }

    @Test
    fun `the largest raster is counted exactly`() {
        val side = 1024
        val packed = IntArray(side * side) { (it shl 8) or (it and 1) }
        val expected = side * side / 2

        assertEquals(expected, RasterImportPlanner.colorCount(raster(side, side, *packed)))
    }
}
