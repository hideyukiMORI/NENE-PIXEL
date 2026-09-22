package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
import io.github.hideyukimori.nenepixel.core.application.workspace.WindowAnchor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.presentation.compose.R
import kotlin.math.roundToInt

/**
 * The committed document drawn over the canvas at an exact device-pixel multiple (ADR 0026).
 *
 * It subscribes only to the committed snapshot, palette definition and window state, so a stroke in
 * progress never redraws it. Drag moves it, a tap cycles the scale, and its pointer events never
 * reach the canvas underneath.
 */
@Composable
internal fun ActualSizeWindowOverlay(
    state: State<EditorRenderState>,
    committed: CommittedBitmapCache,
    callbacks: EditorCallbacks,
) {
    val inputs by remember(state) {
        derivedStateOf {
            ActualSizeWindowInputs(state.value.snapshot, state.value.definition, state.value.actualSizeWindow)
        }
    }
    if (inputs.window.visible) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val frame = with(LocalDensity.current) { WINDOW_FRAME.roundToPx() }
            val area = IntSize(constraints.maxWidth, constraints.maxHeight)
            ActualSizeWindowSurface(
                inputs,
                actualSizeWindowGeometry(inputs.snapshot.size, inputs.window, area, frame),
                committed,
                callbacks,
            )
        }
    }
}

@Composable
private fun ActualSizeWindowSurface(
    inputs: ActualSizeWindowInputs,
    geometry: ActualSizeWindowGeometry,
    committed: CommittedBitmapCache,
    callbacks: EditorCallbacks,
) {
    val window = inputs.window
    var travel by remember(geometry, window) { mutableStateOf(Offset.Zero) }
    val background = PresentationPalette.canvasBackground.toArgb()
    Canvas(
        Modifier
            .absoluteOffset { geometry.place(window.anchor, travel) }
            .exactSize(geometry.width, geometry.height)
            .editorDescription(R.string.actual_size_window, window.scale.devicePixelsPerCell)
            .pointerInput(geometry, window) {
                actualSizeWindowGesture(
                    onMove = { moved -> travel = geometry.reachable(window.anchor, moved) },
                    onRelease = { moved ->
                        val reachable = geometry.reachable(window.anchor, moved)
                        travel = Offset.Zero
                        callbacks.onSetActualSizeWindow(
                            window.withAnchor(geometry.anchorAt(geometry.place(window.anchor, reachable))),
                        )
                    },
                    onTap = {
                        travel = Offset.Zero
                        callbacks.onSetActualSizeWindow(window.withScale(window.scale.next()))
                    },
                )
            },
    ) {
        drawActualSizeWindow(
            geometry,
            committed.render(inputs.snapshot, inputs.definition, background),
            committed.paint,
        )
    }
}

/**
 * One window gesture: movement below the touch slop is a tap that cycles the scale, and movement
 * past it is a drag that starts from the distance already travelled beyond the slop, so the two
 * classifications agree on where the window sits.
 */
private suspend fun PointerInputScope.actualSizeWindowGesture(
    onMove: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
    onTap: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var overSlop = Offset.Zero
        val dragging =
            awaitTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
                onMove(over)
            }
        if (dragging == null) {
            onTap()
        } else {
            trackWindowDrag(dragging.id, overSlop, onMove, onRelease)
        }
    }
}

private suspend fun AwaitPointerEventScope.trackWindowDrag(
    pointerId: PointerId,
    overSlop: Offset,
    onMove: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
) {
    var moved = overSlop
    val completed =
        drag(pointerId) { change ->
            moved += change.positionChange()
            change.consume()
            onMove(moved)
        }
    if (completed) {
        onRelease(moved)
    } else {
        onMove(Offset.Zero)
    }
}

private fun DrawScope.drawActualSizeWindow(
    geometry: ActualSizeWindowGeometry,
    pixels: Bitmap,
    paint: Paint,
) {
    val frame = geometry.frame.toFloat()
    drawRect(PresentationPalette.actualSizeWindowFrame)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(
            pixels,
            geometry.source,
            RectF(frame, frame, frame + geometry.contentWidth, frame + geometry.contentHeight),
            paint,
        )
    }
}

/**
 * The window edge is at most half of the work area's shorter side; a larger scaled document keeps
 * its centre and clips the rest, so every scale stays selectable without new panning state.
 */
private fun actualSizeWindowGeometry(
    canvas: CanvasSize,
    window: ActualSizeWindow,
    area: IntSize,
    frame: Int,
): ActualSizeWindowGeometry {
    val scale = window.scale.devicePixelsPerCell
    val limit = (minOf(area.width, area.height) / 2 - frame * 2).coerceAtLeast(scale)
    val columns = minOf(canvas.width.value, limit / scale).coerceAtLeast(1)
    val rows = minOf(canvas.height.value, limit / scale).coerceAtLeast(1)
    val left = (canvas.width.value - columns) / 2
    val top = (canvas.height.value - rows) / 2
    val free =
        IntSize(
            (area.width - (columns * scale + frame * 2)).coerceAtLeast(0),
            (area.height - (rows * scale + frame * 2)).coerceAtLeast(0),
        )
    return ActualSizeWindowGeometry(Rect(left, top, left + columns, top + rows), scale, frame, free)
}

private fun Modifier.exactSize(
    width: Int,
    height: Int,
): Modifier =
    layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

private data class ActualSizeWindowInputs(
    val snapshot: PixelSnapshot,
    val definition: PaletteDefinition,
    val window: ActualSizeWindow,
)

private data class ActualSizeWindowGeometry(
    val source: Rect,
    val scale: Int,
    val frame: Int,
    val free: IntSize,
) {
    val contentWidth: Int get() = source.width() * scale

    val contentHeight: Int get() = source.height() * scale

    val width: Int get() = contentWidth + frame * 2

    val height: Int get() = contentHeight + frame * 2

    fun place(
        anchor: WindowAnchor,
        travel: Offset,
    ): IntOffset =
        IntOffset(
            (left(anchor) + travel.x.roundToInt()).coerceIn(0, free.width),
            (top(anchor) + travel.y.roundToInt()).coerceIn(0, free.height),
        )

    /**
     * The part of [travel] the window can still take up, so a finger that runs past an edge does not
     * build a dead zone it has to travel back through.
     */
    fun reachable(
        anchor: WindowAnchor,
        travel: Offset,
    ): Offset =
        Offset(
            travel.x.coerceIn(-left(anchor).toFloat(), (free.width - left(anchor)).toFloat()),
            travel.y.coerceIn(-top(anchor).toFloat(), (free.height - top(anchor)).toFloat()),
        )

    fun anchorAt(offset: IntOffset): WindowAnchor =
        WindowAnchor.create(normalized(offset.x, free.width), normalized(offset.y, free.height))

    private fun left(anchor: WindowAnchor): Int = (anchor.x * free.width).roundToInt()

    private fun top(anchor: WindowAnchor): Int = (anchor.y * free.height).roundToInt()

    private fun normalized(
        value: Int,
        free: Int,
    ): Double = if (free <= 0) 0.0 else value.toDouble() / free.toDouble()
}

private val WINDOW_FRAME = 2.dp
