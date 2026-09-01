package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.fail

internal object ViewportTestValues {
    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(domain(CanvasWidth.create(width)), domain(CanvasHeight.create(height)))

    fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(domain(PixelX.create(x)), domain(PixelY.create(y)))

    fun zoom(value: Double): ViewportZoom = viewport(ViewportZoom.create(value))

    fun center(
        x: Double,
        y: Double,
    ): ViewportCenter = viewport(ViewportCenter.create(x, y))

    fun state(
        zoom: Double,
        centerX: Double,
        centerY: Double,
    ): ViewportState = ViewportState.create(zoom(zoom), center(centerX, centerY))

    fun surface(
        width: Int,
        height: Int,
        pixelsPerDp: Double = 1.0,
    ): ViewportSurface = viewport(ViewportSurface.create(width, height, pixelsPerDp))

    fun point(
        x: Double,
        y: Double,
    ): ViewportSurfacePoint = viewport(ViewportSurfacePoint.create(x, y))

    fun transform(
        canvas: CanvasSize,
        surface: ViewportSurface,
        viewport: ViewportState,
    ): ViewportTransform = viewport(ViewportTransform.create(canvas, surface, viewport))

    fun bounds(result: ViewportMappingResult<ViewportSurfaceBounds>): ViewportSurfaceBounds = mapped(result)

    fun pixel(result: ViewportMappingResult<PixelPosition>): PixelPosition = mapped(result)

    fun state(result: ViewportValueResult<ViewportState>): ViewportState = viewport(result)

    fun rejection(result: ViewportValueResult<*>): ViewportValueRejection =
        when (result) {
            is ViewportValueResult.Created -> fail("Expected rejection, but was created: ${result.value}")
            is ViewportValueResult.Rejected -> result.rejection
        }

    private fun <T> domain(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test domain value was rejected: ${result.rejection}")
        }

    private fun <T> viewport(result: ViewportValueResult<T>): T =
        when (result) {
            is ViewportValueResult.Created -> result.value
            is ViewportValueResult.Rejected -> fail("Test viewport value was rejected: ${result.rejection}")
        }

    private fun <T> mapped(result: ViewportMappingResult<T>): T =
        when (result) {
            is ViewportMappingResult.Mapped -> result.value
            ViewportMappingResult.OutsideCanvas -> fail("Expected mapped value, but was outside canvas")
            ViewportMappingResult.OutsideSurface -> fail("Expected mapped value, but was outside surface")
        }
}
