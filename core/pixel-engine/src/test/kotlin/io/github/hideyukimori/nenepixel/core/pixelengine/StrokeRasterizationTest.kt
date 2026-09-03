package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.colorAt
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.revision
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.stroke
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.applied
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationAssertions.rasterized
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationAssertions.rejected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class StrokeRasterizationTest {
    @Test
    fun `rasterization changes only listed pixels without interpolation and inverse restores input`() {
        val canvas = canvas(3, 1)
        val original = snapshot(canvas, pixels = listOf(black, green, black))
        val stroke = stroke(canvas, listOf(position(0, 0), position(2, 0)), red)

        val patch = rasterized(rasterizeStroke(original, stroke))
        val changed = applied(patch.applyTo(original))
        val restored = applied(patch.inverse().applyTo(changed))

        assertEquals(red, colorAt(changed, position(0, 0)))
        assertEquals(green, colorAt(changed, position(1, 0)))
        assertEquals(red, colorAt(changed, position(2, 0)))
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
        assertEquals(red, colorAt(changed, position(0, 0)))
        assertEquals(black, colorAt(changed, position(1, 0)))
        assertEquals(red, colorAt(changed, position(2, 0)))
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
        assertEquals(black, colorAt(smallerSnapshot, position(0, 0)))

        val overflowSnapshot = snapshot(canvas(1, 1), revision(Long.MAX_VALUE))
        val changedStroke = stroke(overflowSnapshot.size, listOf(position(0, 0)), red)
        assertEquals(
            StrokeRasterizationRejection.RevisionOverflow,
            rejected(rasterizeStroke(overflowSnapshot, changedStroke)),
        )
        assertEquals(black, colorAt(overflowSnapshot, position(0, 0)))
    }
}
