package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.center
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.point
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.rejection
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.surface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.zoom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ViewportValueTest {
    @Test
    fun `zoom accepts its closed bounds and rejects non-finite or out-of-range values`() {
        assertEquals(1.0, zoom(1.0).value)
        assertEquals(64.0, zoom(64.0).value)

        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { value ->
            assertTrue(rejection(ViewportZoom.create(value)) is ViewportValueRejection.NonFiniteZoom)
        }
        listOf(Math.nextDown(1.0), Math.nextUp(64.0), -0.0).forEach { value ->
            assertTrue(rejection(ViewportZoom.create(value)) is ViewportValueRejection.ZoomOutsideRange)
        }
    }

    @Test
    fun `center and surface point reject non-finite components and canonicalize negative zero`() {
        val center = center(-0.0, -0.0)
        val point = point(-0.0, -0.0)

        assertPositiveZero(center.x)
        assertPositiveZero(center.y)
        assertPositiveZero(point.xPixels)
        assertPositiveZero(point.yPixels)
        assertTrue(rejection(ViewportCenter.create(Double.NaN, 0.0)) is ViewportValueRejection.NonFiniteCenter)
        assertTrue(
            rejection(ViewportSurfacePoint.create(0.0, Double.POSITIVE_INFINITY)) is
                ViewportValueRejection.NonFiniteSurfacePoint,
        )
    }

    @Test
    fun `surface rejects non-positive dimensions and invalid density`() {
        assertTrue(
            rejection(ViewportSurface.create(0, 1, 1.0)) is ViewportValueRejection.NonPositiveSurfaceWidth,
        )
        assertTrue(
            rejection(ViewportSurface.create(1, -1, 1.0)) is ViewportValueRejection.NonPositiveSurfaceHeight,
        )
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { density ->
            assertTrue(
                rejection(ViewportSurface.create(1, 1, density)) is
                    ViewportValueRejection.NonFinitePixelsPerDp,
            )
        }
        listOf(-1.0, -0.0, 0.0).forEach { density ->
            assertTrue(
                rejection(ViewportSurface.create(1, 1, density)) is
                    ViewportValueRejection.NonPositivePixelsPerDp,
            )
        }
    }

    @Test
    fun `initial viewport uses fit zoom and document-edge midpoint`() {
        val initial = ViewportState.initial(canvas(31, 7))

        assertEquals(1.0, initial.zoom.value)
        assertEquals(15.5, initial.center.x)
        assertEquals(3.5, initial.center.y)
        assertEquals(3.0, surface(1, 1, 3.0).pixelsPerDp)
    }

    private fun assertPositiveZero(value: Double) {
        assertEquals(0L, value.toRawBits())
    }
}
