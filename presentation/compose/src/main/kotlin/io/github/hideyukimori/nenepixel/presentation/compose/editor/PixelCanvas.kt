package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.input.fixedCanvasPointerInput

@Composable
internal fun PixelCanvas(
    renderState: EditorRenderState,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
    modifier: Modifier,
) {
    val pixels = remember(renderState.snapshot) { renderState.snapshot.toRenderedPixels() }
    val preview = remember(renderState.preview) { renderState.preview.toPositions() }
    val canvas = renderState.snapshot.size
    Canvas(
        modifier =
            modifier
                .background(PresentationPalette.canvasBackground)
                .semantics { contentDescription = canvas.accessibilityDescription() }
                .fixedCanvasPointerInput(canvas, callbacks, onRenderStateChanged),
    ) {
        drawPixels(canvas, pixels)
        drawPreview(canvas, preview, renderState.activeColor.toComposeColor())
        drawGrid(canvas)
    }
}

private fun DrawScope.drawPixels(
    canvas: CanvasSize,
    pixels: List<RenderedPixel>,
) {
    val cell = cellSize(canvas)
    pixels.forEach { pixel ->
        drawRect(
            color = pixel.color,
            topLeft = Offset(pixel.x * cell.width, pixel.y * cell.height),
            size = cell,
        )
    }
}

private fun DrawScope.drawPreview(
    canvas: CanvasSize,
    preview: List<PixelPosition>,
    color: Color,
) {
    val cell = cellSize(canvas)
    preview.forEach { position ->
        drawRect(
            color = color.copy(alpha = PREVIEW_ALPHA),
            topLeft = Offset(position.x.value * cell.width, position.y.value * cell.height),
            size = cell,
        )
    }
}

private fun DrawScope.drawGrid(canvas: CanvasSize) {
    val cell = cellSize(canvas)
    repeat(canvas.width.value + 1) { x ->
        val coordinate = x * cell.width
        drawLine(PresentationPalette.grid, Offset(coordinate, 0.0f), Offset(coordinate, size.height), GRID_WIDTH)
    }
    repeat(canvas.height.value + 1) { y ->
        val coordinate = y * cell.height
        drawLine(PresentationPalette.grid, Offset(0.0f, coordinate), Offset(size.width, coordinate), GRID_WIDTH)
    }
}

private fun DrawScope.cellSize(canvas: CanvasSize): Size =
    Size(size.width / canvas.width.value.toFloat(), size.height / canvas.height.value.toFloat())

private fun PixelSnapshot.toRenderedPixels(): List<RenderedPixel> =
    List(size.pixelCount.toInt()) { index ->
        val x = index % size.width.value
        val y = index / size.width.value
        val position = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())
        RenderedPixel(x, y, colorAt(position).requiredValue().toComposeColor())
    }

private fun ToolGesture?.toPositions(): List<PixelPosition> =
    if (this == null) emptyList() else buildList { forEachPosition(::add) }

internal fun PixelColor.toComposeColor(): Color =
    Color(red.value.toInt(), green.value.toInt(), blue.value.toInt(), alpha.value.toInt())

private fun CanvasSize.accessibilityDescription(): String = "${width.value} by ${height.value} pixel canvas"

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Validated render projection was rejected: $rejection")
    }

private data class RenderedPixel(
    val x: Int,
    val y: Int,
    val color: Color,
)

private const val PREVIEW_ALPHA: Float = 0.55f
private const val GRID_WIDTH: Float = 1.0f
