package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.BLACK
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.GREEN
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.WHITE
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.color
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.coverage
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.indices
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.planned
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.raster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class RasterImportConvertingTest {
    @Test
    fun `exact colours take their slot and the source instance stays the target`() {
        val source = palette(BLACK, RED, GREEN)
        val plan = planned(RasterImportPlanner.converting(raster(2, 1, GREEN, RED), canvas(2, 1), source))

        assertSame(source, plan.target)
        assertEquals(listOf(2, 1), indices(plan))
        assertEquals(listOf(1, 1), coverage(plan))
        assertEquals(0, plan.appendedColorCount)
        assertEquals(0, plan.loss.nearestColorCount)
    }

    @Test
    fun `other colours take the nearest entry and a tie takes the lower slot`() {
        val source = palette(WHITE, color(10, 0, 0), color(0, 10, 0))
        val between = color(5, 5, 0)
        val nearGreen = color(0, 9, 0)
        val raster = raster(4, 1, between, between, nearGreen, WHITE)
        val plan = planned(RasterImportPlanner.converting(raster, canvas(4, 1), source))

        assertSame(source, plan.target)
        assertEquals(listOf(1, 1, 2, 0), indices(plan))
        assertEquals(listOf(1, 1, 1, 1), coverage(plan))
        assertEquals(2, plan.loss.nearestColorCount)
    }
}
