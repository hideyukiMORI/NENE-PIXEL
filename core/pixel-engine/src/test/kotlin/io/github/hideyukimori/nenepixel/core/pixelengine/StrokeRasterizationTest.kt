package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.eraserStroke
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.index
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.indexAt
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.revision
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.stroke
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.applied
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.created
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationAssertions.rasterized
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationAssertions.rejected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class StrokeRasterizationTest {
    @Test
    fun `target above packed U8 storage is rejected before rasterization`() {
        val canvas = canvas(1, 1)
        val rejection = rejected(rasterizeStroke(snapshot(canvas), stroke(canvas, listOf(position(0, 0)), index(256))))

        assertEquals(StrokeRasterizationRejection.TargetIndexAboveStorageMaximum(index(256), 255), rejection)
    }

    @Test
    fun `rasterization changes only listed pixels without interpolation and inverse restores input`() {
        val canvas = canvas(3, 1)
        val original = snapshot(canvas, pixels = listOf(black, green, black))
        val stroke = stroke(canvas, listOf(position(0, 0), position(2, 0)), red)

        val patch = rasterized(rasterizeStroke(original, stroke))
        val changed = applied(patch.applyTo(original))
        val restored = applied(patch.inverse().applyTo(changed))

        assertEquals(red, indexAt(changed, position(0, 0)))
        assertEquals(green, indexAt(changed, position(1, 0)))
        assertEquals(red, indexAt(changed, position(2, 0)))
        assertEquals(region(canvas, position(0, 0), canvas(3, 1)), patch.affectedRegion)
        assertEquals(original, restored)
    }

    @Test
    fun `overlapping positions are canonicalized and repeated input is deterministic`() {
        val canvas = canvas(2, 1)
        val original = snapshot(canvas)
        val stroke =
            stroke(
                canvas,
                listOf(position(1, 0), position(0, 0), position(1, 0), position(0, 0)),
                red,
            )

        val first = rasterized(rasterizeStroke(original, stroke))
        val second = rasterized(rasterizeStroke(original, stroke))

        assertEquals(2, first.changeCount)
        assertEquals(first, second)
        assertEquals(applied(first.applyTo(original)), applied(second.applyTo(original)))
    }

    @Test
    fun `contiguous row major path computes its exact region without a second position scan`() {
        val canvas = canvas(4, 2)
        val original = snapshot(canvas)
        val path = listOf(position(1, 1), position(2, 1))

        val patch = rasterized(rasterizeStroke(original, stroke(canvas, path, red)))

        assertEquals(region(canvas, position(1, 1), canvas(2, 1)), patch.affectedRegion)
    }

    @Test
    fun `already colored positions do not expand the effective patch or invalidation`() {
        val canvas = canvas(3, 1)
        val original = snapshot(canvas, pixels = listOf(red, black, black))
        val stroke = stroke(canvas, listOf(position(0, 0), position(2, 0)), red)

        val patch = rasterized(rasterizeStroke(original, stroke))
        val changed = applied(patch.applyTo(original))

        assertEquals(1, patch.changeCount)
        assertEquals(region(canvas, position(2, 0), canvas(1, 1)), patch.affectedRegion)
        assertEquals(red, indexAt(changed, position(0, 0)))
        assertEquals(black, indexAt(changed, position(1, 0)))
        assertEquals(red, indexAt(changed, position(2, 0)))
    }

    @Test
    fun `eraser writes Empty over covered cells through the same patch and inverse path`() {
        val canvas = canvas(3, 1)
        val original = snapshot(canvas, pixels = listOf(red, green, black))
        val stroke = eraserStroke(canvas, listOf(position(0, 0), position(2, 0), position(0, 0)))

        val patch = rasterized(rasterizeStroke(original, stroke))
        val changed = applied(patch.applyTo(original))
        val restored = applied(patch.inverse().applyTo(changed))

        assertEquals(2, patch.changeCount)
        assertEquals(
            created(
                PixelPatch.create(
                    canvas,
                    original.revision,
                    listOf(
                        PixelChange.create(position(0, 0), PixelCell.Covered(red), PixelCell.Empty),
                        PixelChange.create(position(2, 0), PixelCell.Covered(black), PixelCell.Empty),
                    ),
                ),
            ),
            patch,
        )
        assertEquals(PixelCell.Empty, cellAt(changed, position(0, 0)))
        assertEquals(PixelCell.Covered(green), cellAt(changed, position(1, 0)))
        assertEquals(PixelCell.Empty, cellAt(changed, position(2, 0)))
        assertEquals(PixelCell.Covered(red), cellAt(restored, position(0, 0)))
        assertEquals(PixelCell.Covered(black), cellAt(restored, position(2, 0)))
        assertEquals(original, restored)
    }

    @Test
    fun `erasing Empty cells shares the canonical no changes result`() {
        val canvas = canvas(2, 1)
        val original = PixelSnapshot.createEmpty(canvas, revision(Long.MAX_VALUE))

        assertEquals(
            StrokeRasterizationResult.NoChanges,
            rasterizeStroke(original, eraserStroke(canvas, listOf(position(0, 0), position(1, 0)))),
        )
    }

    @Test
    fun `eraser records only covered cells when the path crosses Empty cells`() {
        val canvas = canvas(3, 1)
        val covered = snapshot(canvas)
        val middle = rasterized(rasterizeStroke(covered, eraserStroke(canvas, listOf(position(1, 0)))))
        val half = applied(middle.applyTo(covered))
        val fullPath = listOf(position(0, 0), position(1, 0), position(2, 0))
        val patch = rasterized(rasterizeStroke(half, eraserStroke(canvas, fullPath)))

        assertEquals(2, patch.changeCount)
        assertEquals(PixelCell.Empty, cellAt(applied(patch.applyTo(half)), position(0, 0)))
    }

    @Test
    fun `painting over Empty records Empty to Covered and its inverse restores Empty`() {
        val canvas = canvas(1, 1)
        val original = PixelSnapshot.createEmpty(canvas, Revision.initial())
        val patch = rasterized(rasterizeStroke(original, stroke(canvas, listOf(position(0, 0)), red)))
        val changed = applied(patch.applyTo(original))

        assertEquals(PixelCell.Covered(red), cellAt(changed, position(0, 0)))
        assertEquals(original, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `no changes has one result even at maximum revision`() {
        val canvas = canvas(1, 1)
        val original = snapshot(canvas, revision(Long.MAX_VALUE), listOf(red))
        val stroke = stroke(canvas, listOf(position(0, 0), position(0, 0)), red)

        assertEquals(StrokeRasterizationResult.NoChanges, rasterizeStroke(original, stroke))
    }

    @Test
    fun `canvas mismatch and revision overflow are typed rejections`() {
        val largerCanvas = canvas(2, 1)
        val outsideStroke = stroke(largerCanvas, listOf(position(1, 0)), red)
        val smallerSnapshot = snapshot(canvas(1, 1))
        val outside = rejected(rasterizeStroke(smallerSnapshot, outsideStroke))

        val canvasRejection =
            assertInstanceOf(StrokeRasterizationRejection.CanvasMismatch::class.java, outside)
        assertEquals(largerCanvas, canvasRejection.expected)
        assertEquals(smallerSnapshot.size, canvasRejection.actual)
        assertEquals(black, indexAt(smallerSnapshot, position(0, 0)))

        val overflowSnapshot = snapshot(canvas(1, 1), revision(Long.MAX_VALUE))
        val changedStroke = stroke(overflowSnapshot.size, listOf(position(0, 0)), red)
        assertEquals(
            StrokeRasterizationRejection.RevisionOverflow,
            rejected(rasterizeStroke(overflowSnapshot, changedStroke)),
        )
        assertEquals(black, indexAt(overflowSnapshot, position(0, 0)))
    }
}
