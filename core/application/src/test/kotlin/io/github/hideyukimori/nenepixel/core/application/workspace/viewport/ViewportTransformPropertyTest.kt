package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.bounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.point
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.position
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.surface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTestValues.transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.random.Random

internal class ViewportTransformPropertyTest {
    @Test
    fun `fixed-seed mapped surface points are contained by their canonical forward bounds`() {
        val random = Random(FIXED_SEED)

        repeat(PROPERTY_CASES) {
            val fixture = randomFixture(random)
            val point =
                point(
                    random.nextDouble() * fixture.surface.widthPixels,
                    random.nextDouble() * fixture.surface.heightPixels,
                )
            assertMappedPointAgreesWithBounds(fixture.transform, point)
        }
    }

    @Test
    fun `fixed-seed visible pixel midpoints round-trip through inverse mapping`() {
        val random = Random(FIXED_SEED)

        repeat(PROPERTY_CASES) {
            val fixture = randomFixture(random)
            val position = position(random.nextInt(fixture.canvasWidth), random.nextInt(fixture.canvasHeight))
            val bounds = bounds(fixture.transform.toSurfaceBounds(position))
            val midpoint = point((bounds.left + bounds.right) / 2.0, (bounds.top + bounds.bottom) / 2.0)
            if (fixture.contains(midpoint)) {
                assertEquals(ViewportMappingResult.Mapped(position), fixture.transform.toPixelPosition(midpoint))
            }
        }
    }

    @Test
    fun `fixed-seed canonical internal edges assign exact and adjacent values consistently`() {
        val random = Random(FIXED_SEED)

        repeat(PROPERTY_CASES) {
            val fixture = fitFixture(random)
            val rightX = random.nextInt(1, fixture.canvasWidth)
            val y = random.nextInt(fixture.canvasHeight)
            val leftPixel = position(rightX - 1, y)
            val rightPixel = position(rightX, y)
            val leftBounds = bounds(fixture.transform.toSurfaceBounds(leftPixel))
            val yMidpoint = (leftBounds.top + leftBounds.bottom) / 2.0

            assertEquals(rightPixel, mapped(fixture.transform, leftBounds.right, yMidpoint))
            assertEquals(leftPixel, mapped(fixture.transform, Math.nextDown(leftBounds.right), yMidpoint))
        }
    }

    private fun assertMappedPointAgreesWithBounds(
        transform: ViewportTransform,
        point: ViewportSurfacePoint,
    ) {
        when (val result = transform.toPixelPosition(point)) {
            is ViewportMappingResult.Mapped -> {
                val bounds = bounds(transform.toSurfaceBounds(result.value))
                assertTrue(point.xPixels >= bounds.left && point.xPixels < bounds.right)
                assertTrue(point.yPixels >= bounds.top && point.yPixels < bounds.bottom)
            }

            ViewportMappingResult.OutsideCanvas -> {
                return
            }

            ViewportMappingResult.OutsideSurface -> {
                fail("Generated point was inside the surface")
            }
        }
    }

    private fun randomFixture(random: Random): Fixture {
        val canvasWidth = random.nextInt(1, MAX_CANVAS_DIMENSION)
        val canvasHeight = random.nextInt(1, MAX_CANVAS_DIMENSION)
        val surfaceWidth = random.nextInt(1, MAX_SURFACE_DIMENSION)
        val surfaceHeight = random.nextInt(1, MAX_SURFACE_DIMENSION)
        val canvas = canvas(canvasWidth, canvasHeight)
        val surface = surface(surfaceWidth, surfaceHeight, random.nextDouble(0.5, 4.0))
        val viewport =
            state(
                random.nextDouble(1.0, 64.0),
                random.nextDouble(-canvasWidth.toDouble(), canvasWidth * 2.0),
                random.nextDouble(-canvasHeight.toDouble(), canvasHeight * 2.0),
            )
        return Fixture(canvasWidth, canvasHeight, surface, transform(canvas, surface, viewport))
    }

    private fun fitFixture(random: Random): Fixture {
        val canvasWidth = random.nextInt(2, MAX_CANVAS_DIMENSION)
        val canvasHeight = random.nextInt(1, MAX_CANVAS_DIMENSION)
        val surface =
            surface(
                random.nextInt(1, MAX_SURFACE_DIMENSION),
                random.nextInt(1, MAX_SURFACE_DIMENSION),
            )
        val canvas = canvas(canvasWidth, canvasHeight)
        return Fixture(canvasWidth, canvasHeight, surface, transform(canvas, surface, ViewportState.initial(canvas)))
    }

    private fun mapped(
        transform: ViewportTransform,
        x: Double,
        y: Double,
    ): io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition =
        when (val result = transform.toPixelPosition(point(x, y))) {
            is ViewportMappingResult.Mapped -> result.value
            ViewportMappingResult.OutsideCanvas -> fail("Canonical internal edge was outside canvas")
            ViewportMappingResult.OutsideSurface -> fail("Canonical internal edge was outside surface")
        }

    private data class Fixture(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val surface: ViewportSurface,
        val transform: ViewportTransform,
    ) {
        fun contains(point: ViewportSurfacePoint): Boolean =
            point.xPixels >= 0.0 &&
                point.xPixels < surface.widthPixels.toDouble() &&
                point.yPixels >= 0.0 &&
                point.yPixels < surface.heightPixels.toDouble()
    }

    private companion object {
        const val FIXED_SEED: Int = 0x4E454E45
        const val PROPERTY_CASES: Int = 256
        const val MAX_CANVAS_DIMENSION: Int = 65
        const val MAX_SURFACE_DIMENSION: Int = 513
    }
}
