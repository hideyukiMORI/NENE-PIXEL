package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.bounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.point
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.position
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.surface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnderlayAdjustmentTest {
    private val canvas = canvas(32, 8)

    // Image 16x4 on a 32x8 document fits at scale 2 with its corner at (0, 0).
    private val underlay = ReferenceUnderlay.placed(image(16, 4), canvas)

    // Cell 12.5 surface px, document corner at surface (0, 150).
    private val zoomOne = transform(canvas, surface(400, 400), state(1.0, 16.0, 4.0))

    // Cell 25 surface px.
    private val zoomTwo = transform(canvas, surface(400, 400), state(2.0, 16.0, 4.0))

    @Test
    fun `fixture transforms have the expected cell sizes`() {
        val one = bounds(zoomOne.toSurfaceBounds(position(0, 0)))
        val two = bounds(zoomTwo.toSurfaceBounds(position(0, 0)))

        assertEquals(12.5, one.right - one.left, EPSILON)
        assertEquals(0.0, one.left, EPSILON)
        assertEquals(150.0, one.top, EPSILON)
        assertEquals(25.0, two.bottom - two.top, EPSILON)
    }

    @Test
    fun `the same finger motion moves the placement by the document distance of the current zoom`() {
        val from = point(100.0, 200.0)
        val to = point(125.0, 212.5)

        val atOne = UnderlayAdjustment.moved(underlay, from, to, zoomOne).placement
        val atTwo = UnderlayAdjustment.moved(underlay, from, to, zoomTwo).placement

        assertPlacement(atOne, left = 2.0, top = 1.0, scale = 2.0)
        assertPlacement(atTwo, left = 1.0, top = 0.5, scale = 2.0)
    }

    @Test
    fun `a move past the document edge is clamped to a one-pixel overlap`() {
        val right = UnderlayAdjustment.moved(underlay, point(0.0, 0.0), point(10_000.0, 10_000.0), zoomOne)
        val left = UnderlayAdjustment.moved(underlay, point(0.0, 0.0), point(-10_000.0, -10_000.0), zoomOne)

        assertPlacement(right.placement, left = 31.0, top = 7.0, scale = 2.0)
        assertPlacement(left.placement, left = -31.0, top = -7.0, scale = 2.0)
    }

    @Test
    fun `a pinch scales by the distance ratio and keeps the image point under the centroid`() {
        // Previous centroid (200, 200) = document (16, 4); current centroid (225, 212.5) = document (18, 5).
        val gesture = gesture(point(190.0, 200.0), point(210.0, 200.0), point(212.0, 212.5), point(238.0, 212.5))

        val placement = UnderlayAdjustment.transformed(underlay, gesture, zoomOne).placement

        assertPlacement(placement, left = -2.8, top = -0.2, scale = 2.6)
        // Image point (8, 2) was under the previous centroid and is under the current one.
        assertEquals(8.0, (18.0 - placement.left) / placement.scale, EPSILON)
        assertEquals(2.0, (5.0 - placement.top) / placement.scale, EPSILON)
    }

    @Test
    fun `a two-pointer gesture that keeps its distance only translates`() {
        val gesture = gesture(point(190.0, 200.0), point(210.0, 200.0), point(215.0, 212.5), point(235.0, 212.5))

        val placement = UnderlayAdjustment.transformed(underlay, gesture, zoomOne).placement

        assertPlacement(placement, left = 2.0, top = 1.0, scale = 2.0)
    }

    @Test
    fun `a zero previous distance keeps the scale and still follows the centroid`() {
        val gesture = gesture(point(200.0, 200.0), point(200.0, 200.0), point(200.0, 212.5), point(250.0, 212.5))

        val placement = UnderlayAdjustment.transformed(underlay, gesture, zoomOne).placement

        // Current centroid (225, 212.5) is document (18, 5); previous (200, 200) is document (16, 4).
        assertPlacement(placement, left = 2.0, top = 1.0, scale = 2.0)
    }

    @Test
    fun `overflowing surface distances leave a finite placement`() {
        val max = Double.MAX_VALUE
        val pinch = gesture(point(0.0, 0.0), point(1.0, 1.0), point(-max, -max), point(max, max))
        val drag = UnderlayAdjustment.moved(underlay, point(-max, -max), point(max, max), zoomOne).placement
        val pinched = UnderlayAdjustment.transformed(underlay, pinch, zoomOne).placement

        assertFinite(drag)
        assertFinite(pinched)
        assertEquals(2.0, pinched.scale, EPSILON)
    }

    @Test
    fun `adjusting keeps the image, opacity, visibility and interaction`() {
        val adjusting = underlay.withOpacity(UnderlayOpacity.MAX).adjusting()
        val gesture = gesture(point(190.0, 200.0), point(210.0, 200.0), point(212.0, 212.5), point(238.0, 212.5))

        listOf(
            UnderlayAdjustment.moved(adjusting, point(100.0, 200.0), point(125.0, 212.5), zoomOne),
            UnderlayAdjustment.transformed(adjusting, gesture, zoomOne),
        ).forEach { adjusted ->
            assertSame(adjusting.image, adjusted.image)
            assertEquals(adjusting.canvas, adjusted.canvas)
            assertEquals(UnderlayOpacity.MAX, adjusted.opacity)
            assertEquals(UnderlayVisibility.Shown, adjusted.visibility)
            assertEquals(UnderlayInteraction.Adjusting, adjusted.interaction)
        }
    }

    private fun gesture(
        previousFirst: ViewportSurfacePoint,
        previousSecond: ViewportSurfacePoint,
        currentFirst: ViewportSurfacePoint,
        currentSecond: ViewportSurfacePoint,
    ): ViewportGesture = ViewportGesture.create(previousFirst, previousSecond, currentFirst, currentSecond)

    private fun assertPlacement(
        placement: UnderlayPlacement,
        left: Double,
        top: Double,
        scale: Double,
    ) {
        assertEquals(left, placement.left, EPSILON)
        assertEquals(top, placement.top, EPSILON)
        assertEquals(scale, placement.scale, EPSILON)
    }

    private fun assertFinite(placement: UnderlayPlacement) {
        assertTrue(placement.left.isFinite() && placement.top.isFinite() && placement.scale.isFinite(), "$placement")
        assertTrue(placement.scale > 0.0, "$placement")
    }

    private fun image(
        width: Int,
        height: Int,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, IntArray(width * height))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }

    private companion object {
        // Double arithmetic on surface pixels; 1e-9 document pixels is far below one image pixel.
        const val EPSILON: Double = 1e-9
    }
}
