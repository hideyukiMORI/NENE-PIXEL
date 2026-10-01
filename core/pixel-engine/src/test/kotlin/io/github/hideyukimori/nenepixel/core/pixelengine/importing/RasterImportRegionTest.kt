package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.BLACK
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.BLUE
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.GREEN
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HIDDEN_CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.WHITE
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.coverage
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.indices
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.planned
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.raster
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.targetColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class RasterImportRegionTest {
    @Test
    fun `a raster smaller than the canvas leaves the other cells empty`() {
        val source = palette(BLACK, RED, GREEN)
        val small = raster(2, 1, RED, GREEN)
        val plans =
            listOf(
                planned(RasterImportPlanner.appending(small, canvas(3, 2), source)),
                planned(RasterImportPlanner.converting(small, canvas(3, 2), source)),
            )

        plans.forEach { plan ->
            assertEquals(canvas(3, 2), plan.snapshot.size)
            assertEquals(listOf(1, 2, 0, 0, 0, 0), indices(plan))
            assertEquals(listOf(1, 1, 0, 0, 0, 0), coverage(plan))
            assertEquals(0, plan.loss.droppedPixelCount)
        }
    }

    @Test
    fun `a raster larger than the canvas keeps its top-left part and counts dropped colours only`() {
        val source = palette(BLACK, RED)
        val large = raster(3, 2, RED, GREEN, BLUE, HIDDEN_CLEAR, WHITE, CLEAR)
        val appended = planned(RasterImportPlanner.appending(large, canvas(2, 1), source))

        assertEquals(listOf(BLACK, RED, GREEN), targetColors(appended))
        assertEquals(listOf(1, 2), indices(appended))
        assertEquals(listOf(1, 1), coverage(appended))
        assertEquals(2, appended.loss.droppedPixelCount)

        val converted = planned(RasterImportPlanner.converting(large, canvas(2, 1), source))
        assertEquals(2, converted.loss.droppedPixelCount)
        assertEquals(1, converted.loss.nearestColorCount)
    }

    @Test
    fun `a transparent region imports nothing even with colours outside the canvas`() {
        val source = palette(BLACK, RED)
        val outside = raster(2, 1, HIDDEN_CLEAR, RED)

        assertSame(LayerImportResult.NothingToImport, RasterImportPlanner.appending(outside, canvas(1, 1), source))
        assertSame(LayerImportResult.NothingToImport, RasterImportPlanner.converting(outside, canvas(1, 1), source))
    }

    @Test
    fun `the same input gives the same plan`() {
        val source = palette(BLACK, WHITE)
        val input = raster(3, 2, BLUE, RED, GREEN, RED, HIDDEN_CLEAR, WHITE)
        val forms =
            listOf(
                { RasterImportPlanner.appending(input, canvas(2, 2), source) },
                { RasterImportPlanner.converting(input, canvas(2, 2), source) },
            )

        forms.forEach { form ->
            val first = planned(form())
            val second = planned(form())
            assertEquals(indices(first), indices(second))
            assertEquals(coverage(first), coverage(second))
            assertEquals(targetColors(first), targetColors(second))
            assertEquals(first.target.defaultIndex, second.target.defaultIndex)
            assertEquals(first.loss, second.loss)
        }
    }
}
