package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
 * progress never redraws it. A handle band and a scale chip make the two gestures visible
 * (amendment 2026-09-22, #126): a drag on either band or on the content moves the window, and a tap
 * on the chip or on the window body cycles the scale. Its pointer events never reach the canvas.
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
            val chrome =
                with(LocalDensity.current) {
                    ActualSizeWindowChrome(
                        WINDOW_FRAME.roundToPx(),
                        WINDOW_HANDLE.roundToPx(),
                        WINDOW_FOOTER.roundToPx(),
                        WINDOW_MINIMUM_WIDTH.roundToPx(),
                    )
                }
            val area = IntSize(constraints.maxWidth, constraints.maxHeight)
            ActualSizeWindowSurface(
                inputs,
                actualSizeWindowGeometry(inputs.snapshot.size, inputs.window, area, chrome),
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
    val travel = remember(geometry, window) { mutableStateOf(Offset.Zero) }
    val drag = Modifier.actualSizeWindowDrag(geometry, window, travel, callbacks)
    Column(
        Modifier
            .absoluteOffset { geometry.place(window.anchor, travel.value) }
            .exactSize(geometry.width, geometry.height)
            .background(PresentationPalette.actualSizeWindowFrame)
            .editorDescription(R.string.actual_size_window, window.scale.devicePixelsPerCell),
    ) {
        ActualSizeWindowHandle(drag)
        ActualSizeWindowContent(inputs, geometry, committed, drag)
        ActualSizeWindowFooter(window, drag, callbacks)
    }
}

/** The grip band along the top edge: a visible place to start the move gesture. */
@Composable
private fun ActualSizeWindowHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(WINDOW_HANDLE).testTag(HANDLE_TAG), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(WINDOW_GRIP_WIDTH, WINDOW_GRIP_HEIGHT)
                .background(PresentationPalette.actualSizeWindowLabel, RoundedCornerShape(WINDOW_GRIP_CORNER)),
        )
    }
}

/**
 * The content band. The committed bitmap is transferred at an exact integer multiple and centred in
 * a band that may be wider, so the minimum window width never stretches a cell.
 */
@Composable
private fun ActualSizeWindowContent(
    inputs: ActualSizeWindowInputs,
    geometry: ActualSizeWindowGeometry,
    committed: CommittedBitmapCache,
    modifier: Modifier = Modifier,
) {
    val background = PresentationPalette.canvasBackground.toArgb()
    Box(modifier.exactSize(geometry.width, geometry.contentHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.exactSize(geometry.contentWidth, geometry.contentHeight).testTag(CONTENT_TAG)) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(
                    committed.render(inputs.snapshot, inputs.definition, background),
                    geometry.source,
                    RectF(0f, 0f, geometry.contentWidth.toFloat(), geometry.contentHeight.toFloat()),
                    committed.paint,
                )
            }
        }
    }
}

/** The bottom band: draggable surround plus the scale chip at the trailing corner. */
@Composable
private fun ActualSizeWindowFooter(
    window: ActualSizeWindow,
    modifier: Modifier = Modifier,
    callbacks: EditorCallbacks,
) {
    Row(Modifier.fillMaxWidth().height(WINDOW_FOOTER), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier.weight(1f).fillMaxHeight())
        ActualSizeWindowChip(window.scale.devicePixelsPerCell) {
            callbacks.onSetActualSizeWindow(window.withScale(window.scale.next()))
        }
    }
}

@Composable
private fun ActualSizeWindowChip(
    scale: Int,
    onCycle: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onCycle)
            .editorDescription(R.string.actual_size_window, scale, identity = CHIP_TAG)
            .padding(WINDOW_FRAME),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.actual_size_window_scale, scale),
            modifier =
                Modifier
                    .clip(RoundedCornerShape(WINDOW_CHIP_CORNER))
                    .background(PresentationPalette.actualSizeWindowLabel)
                    .padding(horizontal = WINDOW_CHIP_PADDING),
            color = PresentationPalette.actualSizeWindowFrame,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * One window gesture: movement below the touch slop is a tap that cycles the scale, and movement
 * past it is a drag that starts from the distance already travelled beyond the slop, so the two
 * classifications agree on where the window sits.
 */
private fun Modifier.actualSizeWindowDrag(
    geometry: ActualSizeWindowGeometry,
    window: ActualSizeWindow,
    travel: MutableState<Offset>,
    callbacks: EditorCallbacks,
): Modifier =
    pointerInput(geometry, window) {
        actualSizeWindowGesture(
            onMove = { moved -> travel.value = geometry.reachable(window.anchor, moved) },
            onRelease = { moved ->
                val reachable = geometry.reachable(window.anchor, moved)
                travel.value = Offset.Zero
                callbacks.onSetActualSizeWindow(
                    window.withAnchor(geometry.anchorAt(geometry.place(window.anchor, reachable))),
                )
            },
            onTap = {
                travel.value = Offset.Zero
                callbacks.onSetActualSizeWindow(window.withScale(window.scale.next()))
            },
        )
    }

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

/**
 * The window edge, chrome included, is at most half of the work area's shorter side; a larger scaled
 * document keeps its centre and clips the rest, so every scale stays selectable without new panning
 * state. Whole cells only: at least one, never more than the document holds or the budget allows.
 */
private fun actualSizeWindowGeometry(
    canvas: CanvasSize,
    window: ActualSizeWindow,
    area: IntSize,
    chrome: ActualSizeWindowChrome,
): ActualSizeWindowGeometry {
    val scale = window.scale.devicePixelsPerCell
    val limit = minOf(area.width, area.height) / 2
    val columns = minOf(canvas.width.value, (limit - chrome.frame * 2) / scale).coerceAtLeast(1)
    val rows = minOf(canvas.height.value, (limit - chrome.handle - chrome.footer) / scale).coerceAtLeast(1)
    val left = (canvas.width.value - columns) / 2
    val top = (canvas.height.value - rows) / 2
    val placed =
        ActualSizeWindowGeometry(Rect(left, top, left + columns, top + rows), scale, chrome, IntSize.Zero)
    return placed.copy(
        free =
            IntSize(
                (area.width - placed.width).coerceAtLeast(0),
                (area.height - placed.height).coerceAtLeast(0),
            ),
    )
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

/** The fixed device-pixel bands that surround the content: they carry the window's affordances. */
private data class ActualSizeWindowChrome(
    val frame: Int,
    val handle: Int,
    val footer: Int,
    val minimumWidth: Int,
)

private data class ActualSizeWindowGeometry(
    val source: Rect,
    val scale: Int,
    val chrome: ActualSizeWindowChrome,
    val free: IntSize,
) {
    val contentWidth: Int get() = source.width() * scale

    val contentHeight: Int get() = source.height() * scale

    val width: Int get() = maxOf(chrome.minimumWidth, contentWidth + chrome.frame * 2)

    val height: Int get() = chrome.handle + contentHeight + chrome.footer

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

private const val HANDLE_TAG: String = "editor_actual_size_window_handle"
private const val CONTENT_TAG: String = "editor_actual_size_window_content"
private const val CHIP_TAG: String = "editor_actual_size_window_chip"
private val WINDOW_FRAME = 2.dp
private val WINDOW_HANDLE = 20.dp
private val WINDOW_FOOTER = 22.dp
private val WINDOW_MINIMUM_WIDTH = 96.dp
private val WINDOW_GRIP_WIDTH = 28.dp
private val WINDOW_GRIP_HEIGHT = 3.dp
private val WINDOW_GRIP_CORNER = 2.dp
private val WINDOW_CHIP_CORNER = 3.dp
private val WINDOW_CHIP_PADDING = 5.dp
