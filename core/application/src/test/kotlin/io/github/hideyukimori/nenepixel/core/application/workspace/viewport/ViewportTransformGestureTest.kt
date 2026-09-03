package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.bounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.pixel
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.point
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.position
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.rejection
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.surface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class ViewportTransformGestureTest {
    @Test
    fun `two-pointer ratio preserves previous focal document point under current centroid`() {
        val transform = transform(canvas(100, 100), surface(1000, 1000, 2.0), state(2.0, 50.0, 50.0))
        val next =
            applied(
                transform,
                gesture(
                    point(350.0, 500.0),
                    point(450.0, 500.0),
                    point(500.0, 500.0),
                    point(700.0, 500.0),
                ),
            )

        assertViewport(next, 4.0, 42.5, 50.0)
    }

    @Test
    fun `zoom ratio uses clamped zoom for focal arithmetic at both closed bounds`() {
        val canvas = canvas(100, 100)
        val surface = surface(1000, 1000, 1.0)
        val upper = transform(canvas, surface, state(32.0, 50.0, 50.0))
        val lower = transform(canvas, surface, state(4.0, 50.0, 50.0))

        val upperNext = applied(upper, centeredGesture(100.0, 400.0))
        val lowerNext = applied(lower, centeredGesture(100.0, 0.0))

        assertViewport(upperNext, 64.0, 50.0, 50.0)
        assertViewport(lowerNext, 1.0, 50.0, 50.0)
    }

    @Test
    fun `previous full separation at one dp is pan-only and next-up applies ratio`() {
        val transform = transform(canvas(100, 100), surface(1000, 1000, 2.0), state(4.0, 50.0, 50.0))
        val exact = applied(transform, horizontalGesture(2.0, 200.0))
        val nextUp = applied(transform, horizontalGesture(Math.nextUp(2.0), 200.0))

        assertEquals(4.0, exact.zoom.value)
        assertEquals(64.0, nextUp.zoom.value)
    }

    @Test
    fun `pan and focal center are contextually clamped once`() {
        val transform = transform(canvas(100, 100), surface(100, 100), state(4.0, 50.0, 50.0))
        val gesture =
            gesture(
                point(49.0, 50.0),
                point(51.0, 50.0),
                point(999.0, 50.0),
                point(1001.0, 50.0),
            )

        assertViewport(applied(transform, gesture), 4.0, 12.5, 50.0)
    }

    @Test
    fun `resize exposes normalization target and identity gesture does not retain stale center`() {
        val canvas = canvas(32, 8)
        val stored = state(2.0, 16.0, 6.0)
        val wide = transform(canvas, surface(800, 200), stored)
        val square = transform(canvas, surface(400, 400), stored)
        val identity = centeredGesture(200.0, 200.0)

        assertViewport(wide.viewport, 2.0, 16.0, 6.0)
        assertViewport(square.viewport, 2.0, 16.0, 4.0)
        assertViewport(applied(square, identity), 2.0, 16.0, 4.0)
    }

    @Test
    fun `preferred center is resolved against each explicit canvas witness`() {
        val preferred = state(2.0, 31.0, 4.0)
        val first = transform(canvas(32, 8), surface(400, 400), preferred)
        val second = transform(canvas(16, 16), surface(400, 400), preferred)

        assertViewport(first.viewport, 2.0, 24.0, 4.0)
        assertViewport(second.viewport, 2.0, 12.0, 4.0)
    }

    @Test
    fun `grid threshold is inclusive and density does not alter mapping`() {
        val canvas = canvas(64, 64)
        val viewport = state(2.0, 32.0, 32.0)
        val exact = transform(canvas, surface(256, 256, 1.0), viewport)
        val below = transform(canvas, surface(256, 256, 1.0), state(Math.nextDown(2.0), 32.0, 32.0))
        val above = transform(canvas, surface(256, 256, 1.0), state(Math.nextUp(2.0), 32.0, 32.0))
        val denser = transform(canvas, surface(256, 256, 2.0), viewport)

        assertSame(ViewportGridVisibility.Visible, exact.gridVisibility)
        assertSame(ViewportGridVisibility.Hidden, below.gridVisibility)
        assertSame(ViewportGridVisibility.Visible, above.gridVisibility)
        assertSame(ViewportGridVisibility.Hidden, denser.gridVisibility)
        assertEquals(bounds(exact.toSurfaceBounds(position(7, 9))), bounds(denser.toSurfaceBounds(position(7, 9))))
        assertEquals(
            pixel(exact.toPixelPosition(point(100.0, 120.0))),
            pixel(denser.toPixelPosition(point(100.0, 120.0))),
        )
    }

    @Test
    fun `surface pointer and density scale together preserve mapping and gesture result`() {
        val canvas = canvas(64, 64)
        val viewport = state(4.0, 30.0, 34.0)
        val first = transform(canvas, surface(320, 160, 2.0), viewport)
        val second = transform(canvas, surface(640, 320, 4.0), viewport)
        val firstGesture = gesture(point(80.0, 80.0), point(240.0, 80.0), point(90.0, 70.0), point(270.0, 90.0))
        val secondGesture = gesture(point(160.0, 160.0), point(480.0, 160.0), point(180.0, 140.0), point(540.0, 180.0))

        assertEquals(
            pixel(first.toPixelPosition(point(160.0, 80.0))),
            pixel(second.toPixelPosition(point(320.0, 160.0))),
        )
        assertViewportClose(applied(first, firstGesture), applied(second, secondGesture))
    }

    @Test
    fun `omitting density from physical scale change can change the one-dp classification`() {
        val canvas = canvas(10, 10)
        val viewport = state(4.0, 5.0, 5.0)
        val first = transform(canvas, surface(100, 100, 2.0), viewport)
        val second = transform(canvas, surface(200, 200, 2.0), viewport)

        assertEquals(4.0, applied(first, horizontalGesture(1.5, 6.0)).zoom.value)
        assertEquals(16.0, applied(second, horizontalGesture(3.0, 12.0)).zoom.value)
    }

    @Test
    fun `non-finite derived gesture arithmetic is rejected`() {
        val hugeCanvas = canvas(256, 256)
        val transform = transform(hugeCanvas, surface(1, 1), ViewportState.initial(hugeCanvas))
        val gesture =
            gesture(
                point(Double.MAX_VALUE, Double.MAX_VALUE),
                point(Double.MAX_VALUE, Double.MAX_VALUE),
                point(-Double.MAX_VALUE, -Double.MAX_VALUE),
                point(-Double.MAX_VALUE, -Double.MAX_VALUE),
            )

        assertSame(ViewportValueRejection.UnsafeDerivedGesture, rejection(transform.apply(gesture)))
    }

    private fun centeredGesture(
        previousDistance: Double,
        currentDistance: Double,
    ): ViewportGesture =
        gesture(
            point(500.0 - previousDistance / 2.0, 500.0),
            point(500.0 + previousDistance / 2.0, 500.0),
            point(500.0 - currentDistance / 2.0, 500.0),
            point(500.0 + currentDistance / 2.0, 500.0),
        )

    private fun horizontalGesture(
        previousDistance: Double,
        currentDistance: Double,
    ): ViewportGesture =
        gesture(
            point(0.0, 0.0),
            point(previousDistance, 0.0),
            point(0.0, 0.0),
            point(currentDistance, 0.0),
        )

    private fun gesture(
        previousFirst: ViewportSurfacePoint,
        previousSecond: ViewportSurfacePoint,
        currentFirst: ViewportSurfacePoint,
        currentSecond: ViewportSurfacePoint,
    ): ViewportGesture = ViewportGesture.create(previousFirst, previousSecond, currentFirst, currentSecond)

    private fun applied(
        transform: ViewportTransform,
        gesture: ViewportGesture,
    ): ViewportState = ViewportTestValues.state(transform.apply(gesture))

    private fun assertViewport(
        actual: ViewportState,
        zoom: Double,
        centerX: Double,
        centerY: Double,
    ) {
        assertEquals(zoom, actual.zoom.value, TOLERANCE)
        assertEquals(centerX, actual.center.x, TOLERANCE)
        assertEquals(centerY, actual.center.y, TOLERANCE)
    }

    private fun assertViewportClose(
        first: ViewportState,
        second: ViewportState,
    ) {
        assertEquals(first.zoom.value, second.zoom.value, scaledTolerance(first.zoom.value))
        assertEquals(first.center.x, second.center.x, scaledTolerance(first.center.x))
        assertEquals(first.center.y, second.center.y, scaledTolerance(first.center.y))
    }

    private fun scaledTolerance(value: Double): Double = maxOf(TOLERANCE, abs(value) * TOLERANCE)

    private companion object {
        const val TOLERANCE: Double = 1.0e-12
    }
}
