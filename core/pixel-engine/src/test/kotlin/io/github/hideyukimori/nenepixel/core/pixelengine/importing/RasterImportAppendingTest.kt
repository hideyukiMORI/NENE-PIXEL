package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.BLACK
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.BLUE
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.GREEN
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HALF_RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HIDDEN_CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.WHITE
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.blues
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.color
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.coverage
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.indices
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.planned
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.raster
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.targetColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class RasterImportAppendingTest {
    @Test
    fun `colours already in the palette keep the source instance`() {
        val source = palette(BLACK, RED, GREEN)
        val plan = planned(RasterImportPlanner.appending(raster(2, 1, GREEN, RED), canvas(2, 1), source))

        assertSame(source, plan.target)
        assertEquals(listOf(2, 1), indices(plan))
        assertEquals(listOf(1, 1), coverage(plan))
        assertEquals(0, plan.appendedColorCount)
        assertEquals(0, plan.loss.nearestColorCount)
        assertEquals(0, plan.loss.droppedPixelCount)
    }

    @Test
    fun `new colours are appended in order of first row-major occurrence`() {
        val source = palette(BLACK, WHITE)
        val raster = raster(3, 2, BLUE, RED, BLUE, GREEN, RED, CLEAR)
        val plan = planned(RasterImportPlanner.appending(raster, canvas(3, 2), source))

        assertEquals(listOf(BLACK, WHITE, BLUE, RED, GREEN), targetColors(plan))
        assertEquals(listOf(2, 3, 2, 4, 3, 0), indices(plan))
        assertEquals(listOf(1, 1, 1, 1, 1, 0), coverage(plan))
        assertEquals(3, plan.appendedColorCount)
        assertEquals(0, plan.loss.nearestColorCount)
    }

    @Test
    fun `a colour listed twice takes the lower slot`() {
        val source = palette(BLACK, RED, RED)
        val plan = planned(RasterImportPlanner.appending(raster(1, 1, RED), canvas(1, 1), source))

        assertSame(source, plan.target)
        assertEquals(listOf(1), indices(plan))
    }

    @Test
    fun `a colour that differs only in alpha is another colour`() {
        val source = palette(BLACK, RED)
        val plan = planned(RasterImportPlanner.appending(raster(2, 1, RED, HALF_RED), canvas(2, 1), source))

        assertEquals(listOf(BLACK, RED, HALF_RED), targetColors(plan))
        assertEquals(listOf(1, 2), indices(plan))
    }

    @Test
    fun `transparent pixels are empty whatever their RGB is and are not colours`() {
        val source = palette(BLACK, RED)
        val raster = raster(4, 1, CLEAR, HIDDEN_CLEAR, RED, WHITE and ALPHA_CLEARED)
        val plan = planned(RasterImportPlanner.appending(raster, canvas(4, 1), source))

        assertSame(source, plan.target)
        assertEquals(listOf(0, 0, 1, 0), indices(plan))
        assertEquals(listOf(0, 0, 1, 0), coverage(plan))
        assertEquals(0, plan.loss.nearestColorCount)
    }

    @Test
    fun `colours that no longer fit take the nearest entry of the resulting palette`() {
        val source = palette(*blues(254))
        val nearWhite = color(254, 254, 254)
        val nearRed = color(250, 0, 0)
        val raster = raster(4, 1, WHITE, RED, nearWhite, nearRed)
        val plan = planned(RasterImportPlanner.appending(raster, canvas(4, 1), source))

        assertEquals(256, plan.target.palette.entryCount)
        assertEquals(listOf(WHITE, RED), targetColors(plan).takeLast(2))
        assertEquals(listOf(254, 255, 254, 255), indices(plan))
        assertEquals(2, plan.appendedColorCount)
        assertEquals(2, plan.loss.nearestColorCount)
    }

    @Test
    fun `a full source palette gets nothing appended`() {
        val source = palette(*blues(256))
        val plan = planned(RasterImportPlanner.appending(raster(2, 1, WHITE, blues(256)[10]), canvas(2, 1), source))

        assertSame(source, plan.target)
        assertEquals(listOf(255, 10), indices(plan))
        assertEquals(0, plan.appendedColorCount)
        assertEquals(1, plan.loss.nearestColorCount)
    }

    @Test
    fun `the default slot is unchanged`() {
        val source = palette(BLACK, WHITE, defaultIndex = 1)
        val plan = planned(RasterImportPlanner.appending(raster(1, 1, RED), canvas(1, 1), source))

        assertEquals(listOf(BLACK, WHITE, RED), targetColors(plan))
        assertEquals(source.defaultIndex, plan.target.defaultIndex)
    }

    private companion object {
        const val ALPHA_CLEARED: Int = 0xffffff00.toInt()
    }
}
