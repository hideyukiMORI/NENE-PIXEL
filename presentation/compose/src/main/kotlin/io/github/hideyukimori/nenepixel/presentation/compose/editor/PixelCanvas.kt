package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGridVisibility
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfaceBounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.presentation.compose.input.viewportPointerInput

@Composable
internal fun PixelCanvas(
    renderState: State<EditorRenderState>,
    canvasSize: CanvasSize,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
    modifier: Modifier,
) {
    val pixels = remember { RenderedBitmapCache(PresentationPalette.canvasBackground.toArgb()) }
    val geometries = remember { CanvasGeometryCache() }
    val pixelPaint =
        remember {
            Paint().apply {
                isAntiAlias = false
                isDither = false
                isFilterBitmap = false
            }
        }
    Canvas(
        modifier =
            modifier
                .semantics { contentDescription = canvasSize.accessibilityDescription() }
                .viewportPointerInput(callbacks, onRenderStateChanged),
    ) {
        val current = renderState.value
        val canvas = current.snapshot.size
        val surface = createViewportSurface() ?: return@Canvas
        val geometry = geometries.resolve(canvas, surface, current.viewport) ?: return@Canvas
        drawCanvasMargins(geometry.destination)
        drawPixels(geometry.destination, pixels.render(current.snapshot), pixelPaint)
        drawPreview(geometry.transform, current.preview)
        drawGrid(geometry)
    }
}

private class RenderedBitmapCache(
    private val backgroundArgb: Int,
) {
    private var source: PixelSnapshot? = null
    private var rendered: Bitmap? = null

    fun render(snapshot: PixelSnapshot): Bitmap {
        if (source !== snapshot) {
            source = snapshot
            rendered = snapshot.toOpaqueRenderedBitmap(backgroundArgb)
        }
        return requireNotNull(rendered)
    }
}

private fun DrawScope.drawCanvasMargins(destination: RectF) {
    val coveredLeft = destination.left.coerceIn(0f, size.width)
    val coveredRight = destination.right.coerceIn(0f, size.width)
    val coveredTop = destination.top.coerceIn(0f, size.height)
    val coveredBottom = destination.bottom.coerceIn(0f, size.height)
    if (coveredLeft > 0f) {
        drawRect(PresentationPalette.canvasBackground, size = Size(coveredLeft, size.height))
    }
    if (coveredRight < size.width) {
        drawRect(
            PresentationPalette.canvasBackground,
            topLeft = Offset(coveredRight, 0f),
            size = Size(size.width - coveredRight, size.height),
        )
    }
    if (coveredTop > 0f && coveredRight > coveredLeft) {
        drawRect(
            PresentationPalette.canvasBackground,
            topLeft = Offset(coveredLeft, 0f),
            size = Size(coveredRight - coveredLeft, coveredTop),
        )
    }
    if (coveredBottom < size.height && coveredRight > coveredLeft) {
        drawRect(
            PresentationPalette.canvasBackground,
            topLeft = Offset(coveredLeft, coveredBottom),
            size = Size(coveredRight - coveredLeft, size.height - coveredBottom),
        )
    }
}

private class CanvasGeometryCache {
    private var canvas: CanvasSize? = null
    private var surface: ViewportSurface? = null
    private var viewport: ViewportState? = null
    private var geometry: CanvasGeometry? = null

    fun resolve(
        canvas: CanvasSize,
        surface: ViewportSurface,
        viewport: ViewportState,
    ): CanvasGeometry? {
        if (canvas != this.canvas || surface != this.surface || viewport != this.viewport) {
            this.canvas = canvas
            this.surface = surface
            this.viewport = viewport
            geometry = createCanvasGeometry(canvas, surface, viewport)
        }
        return geometry
    }
}

private class CanvasGeometry(
    val transform: ViewportTransform,
    val destination: RectF,
    val gridPath: Path?,
)

private fun createCanvasGeometry(
    canvas: CanvasSize,
    surface: ViewportSurface,
    viewport: ViewportState,
): CanvasGeometry? {
    val transform = createViewportTransform(canvas, surface, viewport)
    val extent = transform?.canvasProjection(canvas)
    return if (transform == null || extent == null) {
        null
    } else {
        val destination =
            RectF(
                extent.first.left.toFloat(),
                extent.first.top.toFloat(),
                extent.last.right.toFloat(),
                extent.last.bottom.toFloat(),
            )
        val gridPath =
            if (transform.gridVisibility == ViewportGridVisibility.Visible) {
                createGridPath(canvas, transform, destination)
            } else {
                null
            }
        if (transform.gridVisibility == ViewportGridVisibility.Visible && gridPath == null) {
            null
        } else {
            CanvasGeometry(transform, destination, gridPath)
        }
    }
}

private fun createGridPath(
    canvas: CanvasSize,
    transform: ViewportTransform,
    destination: RectF,
): Path? {
    val path = Path()
    var valid = true
    repeat(canvas.width.value) { x ->
        val coordinate = transform.surfaceBounds(pixelPosition(x, 0))?.left?.toFloat()
        if (coordinate == null) {
            valid = false
        } else {
            path.moveTo(coordinate, destination.top)
            path.lineTo(coordinate, destination.bottom)
        }
    }
    if (valid) {
        path.moveTo(destination.right, destination.top)
        path.lineTo(destination.right, destination.bottom)
    }
    repeat(canvas.height.value) { y ->
        val coordinate = transform.surfaceBounds(pixelPosition(0, y))?.top?.toFloat()
        if (coordinate == null) {
            valid = false
        } else {
            path.moveTo(destination.left, coordinate)
            path.lineTo(destination.right, coordinate)
        }
    }
    if (valid) {
        path.moveTo(destination.left, destination.bottom)
        path.lineTo(destination.right, destination.bottom)
    }
    return if (valid) {
        path
    } else {
        null
    }
}

private fun DrawScope.drawPixels(
    destination: RectF,
    pixels: Bitmap,
    paint: Paint,
) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(pixels, null, destination, paint)
    }
}

private fun DrawScope.drawPreview(
    transform: ViewportTransform,
    preview: ToolGesture?,
) {
    val previewColor = preview?.effect?.previewColor() ?: return
    preview.forEachPosition { position ->
        transform.surfaceBounds(position)?.let { bounds -> drawPixel(bounds, previewColor) }
    }
}

private fun StrokeEffect.previewColor(): Color =
    when (this) {
        is StrokeEffect.Paint -> color.toComposeColor().copy(alpha = PREVIEW_ALPHA)
        StrokeEffect.Erase -> PresentationPalette.eraserPreview
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

private fun DrawScope.drawGrid(geometry: CanvasGeometry) {
    val path = geometry.gridPath ?: return
    drawPath(path, PresentationPalette.grid, style = GRID_STROKE)
}

internal fun PixelColor.toComposeColor(): Color =
    Color(red.value.toInt(), green.value.toInt(), blue.value.toInt(), alpha.value.toInt())

private const val PREVIEW_ALPHA: Float = 0.55f
private const val GRID_WIDTH: Float = 1.0f
private val GRID_STROKE = Stroke(width = GRID_WIDTH)
