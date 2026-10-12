package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.bounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.pixel
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.point
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.position
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.surface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.transform
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.toDocumentPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class ViewportTransformDocumentPointTest {
    @Test
    fun `points left of and above the canvas map to negative indices`() {
        val transform = transform(canvas(32, 8), surface(400, 400), state(1.0, 16.0, 4.0))
        val canvasTop = bounds(transform.toSurfaceBounds(position(0, 0))).top

        assertEquals(DocumentPoint.create(0, -1), transform.toDocumentPoint(point(0.0, Math.nextDown(canvasTop))))
        assertEquals(DocumentPoint.create(0, -1), transform.toDocumentPoint(point(0.0, 140.0)))
        assertEquals(DocumentPoint.create(0, -4), transform.toDocumentPoint(point(0.0, 100.0)))
        assertEquals(DocumentPoint.create(0, 0), transform.toDocumentPoint(point(0.0, canvasTop)))
    }

    @Test
    fun `points beyond the right and bottom edges map to indices at or past the extent`() {
        val transform = transform(canvas(32, 8), surface(400, 400), state(2.0, 16.0, 4.0))
        val canvasBottom = bounds(transform.toSurfaceBounds(position(23, 7))).bottom

        assertSame(ViewportMappingResult.OutsideCanvas, transform.toPixelPosition(point(200.0, canvasBottom)))
        assertEquals(DocumentPoint.create(16, 8), transform.toDocumentPoint(point(200.0, canvasBottom)))
        assertEquals(DocumentPoint.create(16, 7), transform.toDocumentPoint(point(200.0, Math.nextDown(canvasBottom))))
        assertEquals(DocumentPoint.create(23, 10), transform.toDocumentPoint(point(399.0, 350.0)))
    }

    @Test
    fun `points inside the canvas agree with the in-canvas mapping including canonical edges`() {
        val fitted = transform(canvas(32, 8), surface(400, 400), state(1.0, 16.0, 4.0))
        val zoomed = transform(canvas(2, 2), surface(24, 3), state(1.3, 1.0, 1.0))
        val canonicalEdge = bounds(zoomed.toSurfaceBounds(position(0, 1))).right
        val fittedPoints = (0 until 400 step 7).map { offset -> point(offset + 0.25, 150.0 + offset % 100) }
        val zoomedPoints = listOf(point(canonicalEdge, 1.5), point(Math.nextDown(canonicalEdge), 1.5))

        fittedPoints.forEach { surfacePoint ->
            assertAgrees(fitted, surfacePoint)
        }
        zoomedPoints.forEach { surfacePoint ->
            assertAgrees(zoomed, surfacePoint)
        }
    }

    @Test
    fun `points outside the surface still map and far points saturate to the Int range`() {
        val transform = transform(canvas(32, 8), surface(400, 400), state(1.0, 16.0, 4.0))

        assertSame(ViewportMappingResult.OutsideSurface, transform.toPixelPosition(point(400.0, 250.0)))
        assertEquals(DocumentPoint.create(32, 8), transform.toDocumentPoint(point(400.0, 250.0)))
        assertEquals(DocumentPoint.create(-3, 36), transform.toDocumentPoint(point(-30.0, 600.0)))
        assertEquals(
            DocumentPoint.create(Int.MIN_VALUE, Int.MAX_VALUE),
            transform.toDocumentPoint(point(-1.0e300, 1.0e300)),
        )
    }

    private fun assertAgrees(
        transform: ViewportTransform,
        surfacePoint: ViewportSurfacePoint,
    ) {
        val expected = pixel(transform.toPixelPosition(surfacePoint)).toDocumentPoint()

        assertEquals(expected, transform.toDocumentPoint(surfacePoint))
    }
}
