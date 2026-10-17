package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.bounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.position
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.rejection
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.surface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.transform
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class ViewportTransformGridProjectionTest {
    @Test
    fun `every visible-grid axis cell maps at admitted dimension zoom and center boundaries`() {
        val canvases = listOf(canvas(1, 1), canvas(1, 256), canvas(256, 1), canvas(17, 253), canvas(256, 256))
        val surfaces =
            listOf(
                surface(1, 1, VISIBLE_DENSITY),
                surface(1, Int.MAX_VALUE, VISIBLE_DENSITY),
                surface(Int.MAX_VALUE, 1, VISIBLE_DENSITY),
                surface(Int.MAX_VALUE, Int.MAX_VALUE, VISIBLE_DENSITY),
                surface(360, 640, VISIBLE_DENSITY),
            )

        canvases.forEach { canvas ->
            surfaces.forEach { surface ->
                verifyViewportBoundaries(canvas, surface)
            }
        }
    }

    @Test
    fun `fixed-seed visible grids map every row and column without surface clipping`() {
        val random = Random(FIXED_SEED)
        repeat(PROPERTY_CASES) {
            val canvas = canvas(random.nextInt(1, 257), random.nextInt(1, 257))
            val surface = surface(random.nextInt(1, Int.MAX_VALUE), random.nextInt(1, Int.MAX_VALUE), VISIBLE_DENSITY)
            val viewport =
                state(
                    random.nextDouble(1.0, 64.0),
                    random.nextDouble(-512.0, 512.0),
                    random.nextDouble(-512.0, 512.0),
                )
            val transform = transform(canvas, surface, viewport)
            assertSame(ViewportGridVisibility.Visible, transform.gridVisibility)
            verifyGridAxes(canvas, transform)
        }
    }

    @Test
    fun `forward grid cells remain mapped when entirely beyond either surface edge`() {
        val canvas = canvas(256, 256)
        val surface = surface(256, 256)
        val towardEnd = transform(canvas, surface, state(64.0, Double.MAX_VALUE, Double.MAX_VALUE))
        val towardStart = transform(canvas, surface, state(64.0, -Double.MAX_VALUE, -Double.MAX_VALUE))
        val beforeSurface = bounds(towardEnd.toSurfaceBounds(position(0, 0)))
        val afterSurface = bounds(towardStart.toSurfaceBounds(position(255, 255)))

        assertSame(ViewportGridVisibility.Visible, towardEnd.gridVisibility)
        assertSame(ViewportGridVisibility.Visible, towardStart.gridVisibility)
        assertTrue(beforeSurface.right < 0.0 && beforeSurface.bottom < 0.0)
        assertTrue(afterSurface.left > surface.widthPixels && afterSurface.top > surface.heightPixels)
        verifyGridAxes(canvas, towardEnd)
        verifyGridAxes(canvas, towardStart)
    }

    @Test
    fun `finite extreme density changes visibility without rejecting contained cells`() {
        val canvas = canvas(256, 256)
        val visible = transform(canvas, surface(1, 1, java.lang.Double.MIN_NORMAL), ViewportState.initial(canvas))
        val hidden = transform(canvas, surface(1, 1, Double.MAX_VALUE), ViewportState.initial(canvas))

        assertSame(ViewportGridVisibility.Visible, visible.gridVisibility)
        assertSame(ViewportGridVisibility.Hidden, hidden.gridVisibility)
        verifyGridAxes(canvas, visible)
        verifyGridAxes(canvas, hidden)
        assertEquals(
            bounds(visible.toSurfaceBounds(position(255, 255))),
            bounds(hidden.toSurfaceBounds(position(255, 255))),
        )
    }

    @Test
    fun `overflowing derived density rejects the whole transform before grid mapping`() {
        val canvas = canvas(256, 256)
        val admittedSurface = surface(1, 1, Double.MIN_VALUE)
        val result = ViewportTransform.create(canvas, admittedSurface, ViewportState.initial(canvas))

        assertSame(ViewportValueRejection.UnsafeDerivedTransform, rejection(result))
    }

    private fun verifyViewportBoundaries(
        canvas: CanvasSize,
        surface: ViewportSurface,
    ) {
        listOf(1.0, 1.3, 64.0).forEach { zoom ->
            val viewports =
                listOf(
                    state(zoom, -Double.MAX_VALUE, -Double.MAX_VALUE),
                    state(zoom, canvas.width.value / 2.0, canvas.height.value / 2.0),
                    state(zoom, Double.MAX_VALUE, Double.MAX_VALUE),
                )
            viewports.forEach { viewport ->
                val transform = transform(canvas, surface, viewport)
                assertSame(ViewportGridVisibility.Visible, transform.gridVisibility)
                verifyGridAxes(canvas, transform)
            }
        }
    }

    private fun verifyGridAxes(
        canvas: CanvasSize,
        transform: ViewportTransform,
    ) {
        repeat(canvas.width.value) { x -> verifyBounds(bounds(transform.toSurfaceBounds(position(x, 0)))) }
        repeat(canvas.height.value) { y -> verifyBounds(bounds(transform.toSurfaceBounds(position(0, y)))) }
        verifyBounds(bounds(transform.toSurfaceBounds(position(canvas.width.value - 1, canvas.height.value - 1))))
    }

    private fun verifyBounds(bounds: ViewportSurfaceBounds) {
        assertTrue(bounds.left < bounds.right && bounds.top < bounds.bottom)
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).forEach { edge ->
            assertTrue(edge.isFinite())
            assertTrue(edge.toFloat().isFinite())
        }
    }

    private companion object {
        const val VISIBLE_DENSITY: Double = 1.0e-6
        const val FIXED_SEED: Int = 0x2394E45
        const val PROPERTY_CASES: Int = 256
    }
}
