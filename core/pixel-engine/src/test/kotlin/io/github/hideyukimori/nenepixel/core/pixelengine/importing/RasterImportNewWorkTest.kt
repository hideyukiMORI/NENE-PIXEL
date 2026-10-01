package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.BLUE
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.GREEN
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HALF_RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.HIDDEN_CLEAR
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.RED
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.color
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportTestValues.raster
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

internal class RasterImportNewWorkTest {
    @Test
    fun `colours take slots in order of first row-major occurrence`() {
        val plan = planned(raster(3, 2, BLUE, RED, BLUE, GREEN, CLEAR, RED))

        assertEquals(listOf(BLUE, RED, GREEN), colors(plan))
        assertEquals(listOf(0, 1, 0, 2, EMPTY, 1), cells(plan))
        assertEquals(PaletteIndex.first, plan.definition.defaultIndex)
        assertEquals(3, plan.snapshot.size.width.value)
        assertEquals(2, plan.snapshot.size.height.value)
    }

    @Test
    fun `transparent pixels are Empty and are not colours whatever their RGB`() {
        val plan = planned(raster(4, 1, HIDDEN_CLEAR, GREEN, CLEAR, BLUE))

        assertEquals(listOf(GREEN, BLUE), colors(plan))
        assertEquals(listOf(EMPTY, 0, EMPTY, 1), cells(plan))
        assertEquals(listOf(0, 0, 0, 1), plan.snapshot.copyPackedIndices().map(Byte::toInt))
    }

    @Test
    fun `colours that differ only in alpha are different colours`() {
        val plan = planned(raster(3, 1, RED, HALF_RED, RED))

        assertEquals(listOf(RED, HALF_RED), colors(plan))
        assertEquals(listOf(0, 1, 0), cells(plan))
    }

    @Test
    fun `one colour gets a duplicate second slot and the default slot is slot 0`() {
        val plan = planned(raster(2, 1, CLEAR, RED))

        assertEquals(listOf(RED, RED), colors(plan))
        assertEquals(listOf(EMPTY, 0), cells(plan))
        assertEquals(PaletteIndex.first, plan.definition.defaultIndex)
    }

    @Test
    fun `exactly 256 colours are planned`() {
        val distinct = IntArray(MAX_COLORS) { distinctColor(MAX_COLORS - 1 - it) }
        val plan = planned(raster(SIDE_16, SIDE_16, *distinct))

        assertEquals(distinct.toList(), colors(plan))
        assertEquals(List(MAX_COLORS) { it }, cells(plan))
    }

    @Test
    fun `257 colours are too many and carry the count`() {
        val packed = IntArray(SIDE_32 * SIDE_9) { distinctColor(minOf(it, MAX_COLORS)) }

        assertEquals(
            NewWorkImportResult.TooManyColors(MAX_COLORS + 1),
            RasterImportPlanner.newWork(raster(SIDE_32, SIDE_9, *packed)),
        )
    }

    @Test
    fun `a side above 256 is above the canvas limit`() {
        val wide = raster(OVER_AXIS, 1, *IntArray(OVER_AXIS) { RED })
        val tall = raster(1, OVER_AXIS, *IntArray(OVER_AXIS) { RED })

        assertEquals(NewWorkImportResult.AboveCanvasLimit, RasterImportPlanner.newWork(wide))
        assertEquals(NewWorkImportResult.AboveCanvasLimit, RasterImportPlanner.newWork(tall))
    }

    @Test
    fun `256 by 256 is planned`() {
        val plan = planned(raster(MAX_AXIS, MAX_AXIS, *IntArray(MAX_AXIS * MAX_AXIS) { RED }))

        assertEquals(MAX_AXIS, plan.snapshot.size.width.value)
        assertEquals(MAX_AXIS, plan.snapshot.size.height.value)
        assertEquals(listOf(RED, RED), colors(plan))
    }

    @Test
    fun `a fully transparent raster has nothing to import`() {
        assertEquals(
            NewWorkImportResult.NothingToImport,
            RasterImportPlanner.newWork(raster(2, 2, CLEAR, HIDDEN_CLEAR, CLEAR, CLEAR)),
        )
    }

    @Test
    fun `the canvas limit is checked before the transparency`() {
        assertEquals(
            NewWorkImportResult.AboveCanvasLimit,
            RasterImportPlanner.newWork(raster(OVER_AXIS, 1, *IntArray(OVER_AXIS) { CLEAR })),
        )
    }

    @Test
    fun `the same raster gives the same plan`() {
        val input = raster(3, 2, BLUE, RED, BLUE, GREEN, CLEAR, RED)
        val first = planned(input)
        val second = planned(input)

        assertNotSame(first, second)
        assertEquals(first.definition, second.definition)
        assertArrayEquals(first.snapshot.copyPackedIndices(), second.snapshot.copyPackedIndices())
        assertArrayEquals(first.snapshot.copyCoverage(), second.snapshot.copyCoverage())
    }

    private fun planned(raster: ImportRaster): NewWorkImportPlan =
        assertInstanceOf(NewWorkImportResult.Planned::class.java, RasterImportPlanner.newWork(raster)).plan

    private fun colors(plan: NewWorkImportPlan): List<Int> =
        plan.definition.palette
            .entries()
            .map { it.color.toPackedRgba8888() }

    /** The slot of each cell, or [EMPTY] for a cell that is not covered. */
    private fun cells(plan: NewWorkImportPlan): List<Int> {
        val indices = plan.snapshot.copyPackedIndices()
        val coverage = plan.snapshot.copyCoverage()
        return List(indices.size) { cell ->
            val covered = (coverage[cell / BYTE_BITS].toInt() ushr (cell % BYTE_BITS)) and 1 == 1
            if (covered) indices[cell].toInt() and U8 else EMPTY
        }
    }

    private fun distinctColor(number: Int): Int = color(0, number / MAX_COLORS, number % MAX_COLORS)

    private companion object {
        const val EMPTY: Int = -1
        const val MAX_COLORS: Int = 256
        const val MAX_AXIS: Int = 256
        const val OVER_AXIS: Int = 257
        const val SIDE_9: Int = 9
        const val SIDE_16: Int = 16
        const val SIDE_32: Int = 32
        const val BYTE_BITS: Int = 8
        const val U8: Int = 0xff
    }
}
