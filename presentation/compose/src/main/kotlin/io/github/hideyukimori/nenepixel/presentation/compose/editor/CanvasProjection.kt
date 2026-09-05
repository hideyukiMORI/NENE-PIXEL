package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfaceBounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.math.roundToInt

internal data class CanvasProjection(
    val first: ViewportSurfaceBounds,
    val last: ViewportSurfaceBounds,
)

internal fun DrawScope.createViewportSurface(): ViewportSurface? =
    when (
        val result =
            ViewportSurface.create(
                widthPixels = size.width.roundToInt(),
                heightPixels = size.height.roundToInt(),
                pixelsPerDp = density.toDouble(),
            )
    ) {
        is ViewportValueResult.Created -> result.value
        is ViewportValueResult.Rejected -> null
    }

internal fun createViewportTransform(
    canvas: CanvasSize,
    surface: ViewportSurface,
    viewport: ViewportState,
): ViewportTransform? =
    when (val result = ViewportTransform.create(canvas, surface, viewport)) {
        is ViewportValueResult.Created -> result.value
        is ViewportValueResult.Rejected -> null
    }

internal fun ViewportTransform.surfaceBounds(position: PixelPosition): ViewportSurfaceBounds? =
    when (val result = toSurfaceBounds(position)) {
        is ViewportMappingResult.Mapped -> result.value

        ViewportMappingResult.OutsideCanvas,
        ViewportMappingResult.OutsideSurface,
        -> null
    }

internal fun ViewportTransform.canvasProjection(canvas: CanvasSize): CanvasProjection? {
    val first = surfaceBounds(pixelPosition(0, 0))
    val last = surfaceBounds(pixelPosition(canvas.width.value - 1, canvas.height.value - 1))
    return if (first == null || last == null) null else CanvasProjection(first, last)
}

internal fun pixelPosition(
    x: Int,
    y: Int,
): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

internal fun CanvasSize.accessibilityDescription(): String = "${width.value} by ${height.value} pixel canvas"

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Validated render projection was rejected: $rejection")
    }
