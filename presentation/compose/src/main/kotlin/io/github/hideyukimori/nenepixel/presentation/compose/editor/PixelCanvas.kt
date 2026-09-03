package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGridVisibility
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfaceBounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.presentation.compose.input.viewportPointerInput

@Composable
internal fun PixelCanvas(
    renderState: EditorRenderState,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
    modifier: Modifier,
) {
    val pixels = remember(renderState.snapshot) { renderState.snapshot.toRenderedBitmap() }
    val pixelPaint =
        remember {
            Paint().apply {
                isAntiAlias = false
                isDither = false
                isFilterBitmap = false
            }
        }
    val canvas = renderState.snapshot.size
    Canvas(
        modifier =
            modifier
                .background(PresentationPalette.canvasBackground)
                .semantics { contentDescription = canvas.accessibilityDescription() }
                .viewportPointerInput(callbacks, onRenderStateChanged),
    ) {
        val surface = createViewportSurface() ?: return@Canvas
        val transform = createViewportTransform(canvas, surface, renderState) ?: return@Canvas
        drawPixels(transform, canvas, pixels, pixelPaint)
        drawPreview(transform, renderState.preview, renderState.activeColor.toComposeColor())
        drawGrid(canvas, transform)
    }
}

private fun DrawScope.drawPixels(
    transform: ViewportTransform,
    canvas: CanvasSize,
    pixels: Bitmap,
    paint: Paint,
) {
    val extent = transform.canvasProjection(canvas) ?: return
    val destination =
        RectF(
            extent.first.left.toFloat(),
            extent.first.top.toFloat(),
            extent.last.right.toFloat(),
            extent.last.bottom.toFloat(),
        )
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(pixels, null, destination, paint)
    }
}

private fun DrawScope.drawPreview(
    transform: ViewportTransform,
    preview: ToolGesture?,
    color: Color,
) {
    val previewColor = color.copy(alpha = PREVIEW_ALPHA)
    preview?.forEachPosition { position ->
        transform.surfaceBounds(position)?.let { bounds -> drawPixel(bounds, previewColor) }
    }
}

private fun DrawScope.drawPixel(
    bounds: ViewportSurfaceBounds,
    color: Color,
) {
    drawRect(
        color = color,
        topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
        size = Size((bounds.right - bounds.left).toFloat(), (bounds.bottom - bounds.top).toFloat()),
    )
}

private fun DrawScope.drawGrid(
    canvas: CanvasSize,
    transform: ViewportTransform,
) {
    if (transform.gridVisibility != ViewportGridVisibility.Visible) return
    val extent = transform.canvasProjection(canvas) ?: return
    repeat(canvas.width.value) { x ->
        val bounds = transform.surfaceBounds(pixelPosition(x, 0)) ?: return@repeat
        drawLine(bounds.left.toFloat(), extent.first.top.toFloat(), extent.last.bottom.toFloat(), vertical = true)
    }
    drawLine(extent.last.right.toFloat(), extent.first.top.toFloat(), extent.last.bottom.toFloat(), vertical = true)
    repeat(canvas.height.value) { y ->
        val bounds = transform.surfaceBounds(pixelPosition(0, y)) ?: return@repeat
        drawLine(bounds.top.toFloat(), extent.first.left.toFloat(), extent.last.right.toFloat(), vertical = false)
    }
    drawLine(extent.last.bottom.toFloat(), extent.first.left.toFloat(), extent.last.right.toFloat(), vertical = false)
}

private fun DrawScope.drawLine(
    coordinate: Float,
    start: Float,
    end: Float,
    vertical: Boolean,
) {
    val from = if (vertical) Offset(coordinate, start) else Offset(start, coordinate)
    val to = if (vertical) Offset(coordinate, end) else Offset(end, coordinate)
    drawLine(PresentationPalette.grid, from, to, GRID_WIDTH)
}

internal fun PixelColor.toComposeColor(): Color =
    Color(red.value.toInt(), green.value.toInt(), blue.value.toInt(), alpha.value.toInt())

private const val PREVIEW_ALPHA: Float = 0.55f
private const val GRID_WIDTH: Float = 1.0f
