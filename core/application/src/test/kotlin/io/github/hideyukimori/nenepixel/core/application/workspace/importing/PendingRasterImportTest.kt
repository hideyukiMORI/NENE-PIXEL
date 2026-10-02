package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0033: a pick is planned once into facts and one option per form; the raster is not kept. */
internal class PendingRasterImportTest {
    @Test
    fun `a raster whose colours are all in the palette plans both forms without appending or converting`() {
        val pending = PendingRasterImport.planned(raster(2, 1, RED, BLACK), canvas(2, 1), defaultDefinition)

        assertEquals(RasterImportFacts(2, 1, 2), pending.facts)
        val appending = plan(pending.appending)
        assertEquals(0, appending.appendedColorCount)
        assertSame(defaultDefinition, appending.source)
        val converting = plan(pending.converting)
        assertEquals(0, converting.appendedColorCount)
        assertEquals(0, converting.loss.nearestColorCount)
        assertEquals(0, converting.loss.droppedPixelCount)
    }

    @Test
    fun `a new colour is appended by one form and takes the nearest colour in the other`() {
        val pending = PendingRasterImport.planned(raster(1, 1, BLUE), canvas(1, 1), defaultDefinition)

        assertEquals(RasterImportFacts(1, 1, 1), pending.facts)
        assertEquals(1, plan(pending.appending).appendedColorCount)
        val converting = plan(pending.converting)
        assertEquals(0, converting.appendedColorCount)
        assertEquals(1, converting.loss.nearestColorCount)
    }

    @Test
    fun `a fully transparent raster has nothing to import in either form`() {
        val pending = PendingRasterImport.planned(raster(2, 1, CLEAR, CLEAR), canvas(2, 1), defaultDefinition)

        assertEquals(RasterImportFacts(2, 1, 0), pending.facts)
        assertSame(RasterImportOption.NothingToImport, pending.appending)
        assertSame(RasterImportOption.NothingToImport, pending.converting)
    }

    @Test
    fun `pixels outside a smaller canvas are counted as dropped in both forms`() {
        val pending = PendingRasterImport.planned(raster(3, 1, RED, RED, GREEN), canvas(2, 1), defaultDefinition)

        assertEquals(RasterImportFacts(3, 1, 2), pending.facts)
        assertEquals(1, plan(pending.appending).loss.droppedPixelCount)
        assertEquals(1, plan(pending.converting).loss.droppedPixelCount)
    }

    @Test
    fun `equality is identity and the text shows only the facts`() {
        val first = PendingRasterImport.planned(raster(1, 1, RED), canvas(1, 1), defaultDefinition)
        val second = PendingRasterImport.planned(raster(1, 1, RED), canvas(1, 1), defaultDefinition)

        assertEquals(first, first)
        assertNotEquals(first, second)
        assertEquals("PendingRasterImport(facts=${first.facts})", first.toString())
    }

    @Test
    fun `a small raster with two colours can be opened as a new work of its size`() {
        val pending = PendingRasterImport.planned(raster(2, 1, RED, GREEN), canvas(1, 1), defaultDefinition)

        val plan = assertInstanceOf(NewWorkImportOption.Available::class.java, pending.newWork).plan
        assertEquals(2, plan.definition.palette.entryCount)
        assertEquals(canvas(2, 1), plan.snapshot.size)
    }

    @Test
    fun `a raster wider than the canvas limit cannot be a new work but is cropped into a layer`() {
        val pending =
            PendingRasterImport.planned(raster(WIDE, 1, *IntArray(WIDE) { RED }), canvas(2, 1), defaultDefinition)

        assertSame(NewWorkImportOption.AboveCanvasLimit, pending.newWork)
        assertInstanceOf(RasterImportOption.Available::class.java, pending.appending)
    }

    @Test
    fun `a raster with more colours than a palette holds cannot be a new work and the facts keep the count`() {
        val colors = IntArray(2 * HALF) { index -> (minOf(index, WIDE - 1) shl 8) or OPAQUE }
        val pending = PendingRasterImport.planned(raster(HALF, 2, *colors), canvas(2, 1), defaultDefinition)

        assertSame(NewWorkImportOption.TooManyColors, pending.newWork)
        assertEquals(WIDE, pending.facts.colorCount)
    }

    @Test
    fun `a fully transparent raster has nothing to import as a new work`() {
        val pending = PendingRasterImport.planned(raster(2, 1, CLEAR, CLEAR), canvas(2, 1), defaultDefinition)

        assertSame(NewWorkImportOption.NothingToImport, pending.newWork)
    }

    private fun plan(option: RasterImportOption): LayerImportPlan =
        assertInstanceOf(RasterImportOption.Available::class.java, option).plan

    private fun raster(
        width: Int,
        height: Int,
        vararg packed: Int,
    ): ImportRaster =
        when (val result = ImportRaster.create(width, height, packed)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Raster rejected: ${result.rejection}")
        }

    private companion object {
        const val BLACK: Int = 0x000000ff
        const val RED: Int = 0xff0000ff.toInt()
        const val GREEN: Int = 0x00ff00ff
        const val BLUE: Int = 0x0000ffff
        const val CLEAR: Int = 0
        const val OPAQUE: Int = 0xff
        const val WIDE: Int = 257
        const val HALF: Int = 129
    }
}
