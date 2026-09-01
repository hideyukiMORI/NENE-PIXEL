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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class ViewportTransformMappingTest {
    @Test
    fun `fit uses real arithmetic and one square cell scale across a cartesian matrix`() {
        val canvases = listOf(Dimensions(1, 1), Dimensions(2, 7), Dimensions(32, 8), Dimensions(8, 32))
        val surfaces = listOf(Dimensions(1, 1), Dimensions(3, 16), Dimensions(400, 400))
        val zooms = listOf(1.0, 1.3, 64.0)

        canvases.forEach { canvasDimensions ->
            surfaces.forEach { surfaceDimensions ->
                zooms.forEach { zoom ->
                    assertFit(canvasDimensions, surfaceDimensions, zoom)
                }
            }
        }
    }

    @Test
    fun `rectangular canvas is letterboxed without stretching pixels`() {
        val transform = transform(canvas(32, 8), surface(400, 400), state(1.0, 16.0, 4.0))
        val first = bounds(transform.toSurfaceBounds(position(0, 0)))
        val last = bounds(transform.toSurfaceBounds(position(31, 7)))

        assertBounds(first, 0.0, 150.0, 12.5, 162.5)
        assertBounds(last, 387.5, 237.5, 400.0, 250.0)
    }

    @Test
    fun `canonical exact internal edge maps right while its predecessor maps left`() {
        val transform = transform(canvas(2, 2), surface(24, 3), state(1.3, 1.0, 1.0))
        val canonicalEdge = bounds(transform.toSurfaceBounds(position(0, 1))).right

        assertEquals(12.0, canonicalEdge)
        assertEquals(position(1, 1), pixel(transform.toPixelPosition(point(canonicalEdge, 1.5))))
        assertEquals(position(0, 1), pixel(transform.toPixelPosition(point(Math.nextDown(canonicalEdge), 1.5))))
        assertEquals(position(1, 1), pixel(transform.toPixelPosition(point(Math.nextUp(canonicalEdge), 1.5))))
    }

    @Test
    fun `surface and projected document half-open bounds keep distinct outside results`() {
        val transform = transform(canvas(32, 8), surface(400, 400), state(1.0, 16.0, 4.0))
        val projectedBottom = bounds(transform.toSurfaceBounds(position(16, 7))).bottom

        assertSame(ViewportMappingResult.OutsideSurface, transform.toPixelPosition(point(400.0, 200.0)))
        assertSame(ViewportMappingResult.OutsideSurface, transform.toPixelPosition(point(-0.1, 200.0)))
        assertSame(ViewportMappingResult.OutsideCanvas, transform.toPixelPosition(point(200.0, projectedBottom)))
        assertEquals(
            position(16, 7),
            pixel(transform.toPixelPosition(point(200.0, Math.nextDown(projectedBottom)))),
        )
        assertSame(ViewportMappingResult.OutsideCanvas, transform.toSurfaceBounds(position(32, 0)))
    }

    @Test
    fun `surface-right predecessor need not map to final document pixel at zoom`() {
        val transform = transform(canvas(32, 8), surface(400, 400), state(2.0, 16.0, 4.0))

        assertEquals(
            position(23, 4),
            pixel(transform.toPixelPosition(point(Math.nextDown(400.0), 200.0))),
        )
        assertSame(ViewportMappingResult.OutsideSurface, transform.toPixelPosition(point(400.0, 200.0)))
    }

    @Test
    fun `integer dimensions are widened before fit division`() {
        val transform = transform(canvas(512, 512), surface(320, 160), state(1.0, 256.0, 256.0))
        val first = bounds(transform.toSurfaceBounds(position(0, 0)))

        assertBounds(first, 80.0, 0.0, 80.3125, 0.3125)
    }

    @Test
    fun `finite inputs that collapse adjacent double edges reject transform creation`() {
        val result =
            ViewportTransform.create(
                canvas(Int.MAX_VALUE, 1),
                surface(1, Int.MAX_VALUE),
                ViewportState.initial(canvas(Int.MAX_VALUE, 1)),
            )

        assertSame(ViewportValueRejection.UnsafeDerivedTransform, rejection(result))
    }

    private fun assertFit(
        canvasDimensions: Dimensions,
        surfaceDimensions: Dimensions,
        zoom: Double,
    ) {
        val canvas = canvas(canvasDimensions.width, canvasDimensions.height)
        val surface = surface(surfaceDimensions.width, surfaceDimensions.height)
        val transform = transform(canvas, surface, state(zoom, canvas.width.value / 2.0, canvas.height.value / 2.0))
        val first = bounds(transform.toSurfaceBounds(position(0, 0)))
        val expectedScale =
            minOf(
                surface.widthPixels.toDouble() / canvas.width.value.toDouble(),
                surface.heightPixels.toDouble() / canvas.height.value.toDouble(),
            ) * zoom

        assertClose(expectedScale, first.right - first.left)
        assertClose(expectedScale, first.bottom - first.top)
        assertClose(surface.widthPixels / 2.0 - canvas.width.value / 2.0 * expectedScale, first.left)
        assertClose(surface.heightPixels / 2.0 - canvas.height.value / 2.0 * expectedScale, first.top)
    }

    private fun assertBounds(
        actual: ViewportSurfaceBounds,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ) {
        assertClose(left, actual.left)
        assertClose(top, actual.top)
        assertClose(right, actual.right)
        assertClose(bottom, actual.bottom)
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
    ) {
        val tolerance = maxOf(1.0e-12, abs(expected) * 1.0e-12)
        assertEquals(expected, actual, tolerance)
    }

    private data class Dimensions(
        val width: Int,
        val height: Int,
    )
}
