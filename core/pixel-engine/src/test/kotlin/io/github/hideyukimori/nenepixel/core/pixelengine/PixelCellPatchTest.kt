package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.index
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.revision
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.stroke
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.applicationRejected
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.applied
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.created
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.definition
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.applyPaletteRemap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PixelCellPatchTest {
    @Test
    fun `patch paints an empty cell and empties a covered cell then inverse restores both`() {
        val original = cells(canvas(2, 1), PixelCell.Empty, PixelCell.Covered(red))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    original.revision,
                    listOf(
                        PixelChange.create(position(0, 0), PixelCell.Empty, PixelCell.Covered(green)),
                        PixelChange.create(position(1, 0), PixelCell.Covered(red), PixelCell.Empty),
                    ),
                ),
            )

        val changed = applied(patch.applyTo(original))

        assertEquals(PixelCell.Covered(green), changed.cell(0, 0))
        assertEquals(PixelCell.Empty, changed.cell(1, 0))
        assertEquals(cells(canvas(2, 1), PixelCell.Covered(green), PixelCell.Empty, revision = revision(1L)), changed)
        assertEquals(original, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `inverse swaps before and after coverage`() {
        val original = cells(canvas(1, 1), PixelCell.Empty)
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    original.revision,
                    listOf(PixelChange.create(position(0, 0), PixelCell.Empty, PixelCell.Covered(black))),
                ),
            )
        val inverse = patch.inverse()

        val rejection = applicationRejected(inverse.applyTo(original.withRevision(revision(1L))))

        assertEquals(
            PixelPatchApplicationRejection.BeforeValueMismatch(
                position(0, 0),
                expected = PixelCell.Covered(black),
                actual = PixelCell.Empty,
            ),
            rejection,
        )
        assertEquals(patch, inverse.inverse())
        assertEquals(patch.hashCode(), inverse.inverse().hashCode())
        assertNotEquals(patch, inverse)
    }

    @Test
    fun `patches that differ only in coverage are not equal`() {
        val size = canvas(1, 1)
        val fromEmpty =
            created(
                PixelPatch.create(
                    size,
                    Revision.initial(),
                    listOf(PixelChange.create(position(0, 0), PixelCell.Empty, PixelCell.Covered(red))),
                ),
            )
        val fromBlack =
            created(
                PixelPatch.create(
                    size,
                    Revision.initial(),
                    listOf(PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red))),
                ),
            )

        assertNotEquals(fromEmpty, fromBlack)
    }

    @Test
    fun `before mismatch reports an empty actual cell`() {
        val original = cells(canvas(2, 1), PixelCell.Covered(black), PixelCell.Empty)
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    original.revision,
                    listOf(PixelChange.create(position(1, 0), PixelCell.Covered(black), PixelCell.Covered(red))),
                ),
            )

        assertEquals(
            PixelPatchApplicationRejection.BeforeValueMismatch(
                position(1, 0),
                expected = PixelCell.Covered(black),
                actual = PixelCell.Empty,
            ),
            applicationRejected(patch.applyTo(original)),
        )
    }

    @Test
    fun `last row major position keeps its coverage bits`() {
        val size = canvas(MAX_EDGE, MAX_EDGE)
        val original = PixelSnapshot.createEmpty(size, Revision.initial())
        val last = position(MAX_EDGE - 1, MAX_EDGE - 1)
        val patch =
            created(
                PixelPatch.create(
                    size,
                    original.revision,
                    listOf(PixelChange.create(last, PixelCell.Empty, PixelCell.Covered(red))),
                ),
            )
        val packed =
            created(
                PixelPatch.createFromValidatedPackedIndices(
                    size,
                    original.revision,
                    intArrayOf(packPatchPosition(LAST_INDEX, beforeCovered = false, afterCovered = true)),
                    byteArrayOf(0),
                    byteArrayOf(1),
                    positionsAreContiguous = false,
                ),
            )

        assertEquals(
            LAST_INDEX or AFTER_COVERED,
            packPatchPosition(LAST_INDEX, beforeCovered = false, afterCovered = true),
        )
        assertEquals(
            LAST_INDEX or BOTH_COVERED,
            packPatchPosition(LAST_INDEX, beforeCovered = true, afterCovered = true),
        )
        assertEquals(patch, packed)
        assertEquals(region(size, last, canvas(1, 1)), patch.affectedRegion)
        val changed = applied(patch.applyTo(original))
        assertEquals(PixelCell.Covered(red), changed.cell(MAX_EDGE - 1, MAX_EDGE - 1))
        assertEquals(original, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `remap leaves empty cells untouched`() {
        val original = cells(canvas(3, 1), PixelCell.Empty, PixelCell.Covered(black), PixelCell.Empty)
        val remap = paletteRemap(destinations = listOf(1, 0))

        val result = applyPaletteRemap(original, remap)

        val patch = assertInstanceOf(PaletteRemapApplicationResult.Changed::class.java, result).patch
        assertEquals(1, patch.changeCount)
        assertEquals(
            cells(canvas(3, 1), PixelCell.Empty, PixelCell.Covered(red), PixelCell.Empty, revision = revision(1L)),
            applied(patch.applyTo(original)),
        )
    }

    @Test
    fun `remap reports no changes when only empty cells hold a remapped index`() {
        val original = cells(canvas(2, 1), PixelCell.Empty, PixelCell.Covered(red))
        val remap = paletteRemap(destinations = listOf(1, 1))

        assertEquals(PaletteRemapApplicationResult.NoIndexChanges, applyPaletteRemap(original, remap))
    }

    @Test
    fun `surface snapshot keeps coverage and writes empty as index zero`() {
        val original = cells(canvas(3, 1), PixelCell.Empty, PixelCell.Covered(green), PixelCell.Covered(red))
        val surface = PixelSurface.from(original)

        assertEquals(original, surface.snapshot(original.revision))

        surface.writeCell(1, PixelCell.Empty)
        surface.writeCell(0, PixelCell.Covered(black))

        val written = surface.snapshot(original.revision)
        assertEquals(cells(canvas(3, 1), PixelCell.Covered(black), PixelCell.Empty, PixelCell.Covered(red)), written)
        assertEquals(0, written.copyPackedIndices()[1].toInt())
        assertEquals(PixelCell.Empty, surface.cellAt(position(1, 0)))
    }

    @Test
    fun `stroke over an empty cell records an empty before cell`() {
        val original = cells(canvas(2, 1), PixelCell.Empty, PixelCell.Covered(black))
        val result = rasterizeStroke(original, stroke(original.size, listOf(position(0, 0), position(1, 0)), black))

        val patch = assertInstanceOf(StrokeRasterizationResult.Rasterized::class.java, result).patch

        assertEquals(1, patch.changeCount)
        assertEquals(
            cells(canvas(2, 1), PixelCell.Covered(black), PixelCell.Covered(black), revision = revision(1L)),
            applied(patch.applyTo(original)),
        )
    }

    private fun cells(
        size: CanvasSize,
        vararg cells: PixelCell,
        revision: Revision = Revision.initial(),
    ): PixelSnapshot {
        val surface = PixelSurface.from(PixelSnapshot.createEmpty(size, revision))
        cells.forEachIndexed { rowMajorIndex, cell -> surface.writeCell(rowMajorIndex, cell) }
        return surface.snapshot(revision)
    }

    private fun PixelSnapshot.cell(
        x: Int,
        y: Int,
    ): PixelCell =
        when (val result = cellAt(position(x, y))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test position was rejected: ${result.rejection}")
        }

    private fun paletteRemap(destinations: List<Int>): PaletteRemap {
        val definition = definition(List(destinations.size) { PixelColor.blank })
        return when (val result = PaletteRemap.create(definition, definition, destinations.map(::index))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test remap was rejected: ${result.rejection}")
        }
    }

    private companion object {
        const val MAX_EDGE: Int = 256
        const val LAST_INDEX: Int = 65_535
        const val AFTER_COVERED: Int = 0x2_0000
        const val BOTH_COVERED: Int = 0x3_0000
    }
}
