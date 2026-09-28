package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem

/**
 * The control's own pointer stream: a press opens the menu, the same pointer highlights the item under it, and
 * the release confirms after a drag or a hold. A short tap keeps the menu open in tap mode via [onTapMode]; a tap
 * in tap mode cancels, and a tap while armed disarms. A cancelled pointer stream or leaving composition during a
 * press cancels the menu. Every change is consumed, so nothing reaches the canvas.
 */
internal fun Modifier.quickSelectGesture(
    callbacks: EditorQuickSelectCallbacks,
    session: State<QuickSelectGestureSession>,
    onTapMode: (Boolean) -> Unit,
): Modifier =
    pointerInput(callbacks) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val tap = QuickSelectControlTap.of(session.value)
            if (tap == QuickSelectControlTap.Disarm) {
                val outcome = trackPress(down) {}
                if (outcome == PressOutcome.Tap) callbacks.applyControlTap(tap, onTapMode)
            } else {
                pressMenu(down, QuickSelectPress(callbacks, session, onTapMode, tap))
            }
        }
    }

/**
 * The tap-mode scrim's pointer stream: it takes every pointer over the work area from the down, so nothing reaches
 * the canvas, and a completed press on it cancels the menu.
 */
internal fun Modifier.quickSelectScrimGesture(callbacks: EditorQuickSelectCallbacks): Modifier =
    pointerInput(callbacks) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if (trackPress(down) {} != PressOutcome.Cancel) callbacks.onCancel()
        }
    }

private suspend fun AwaitPointerEventScope.pressMenu(
    down: PointerInputChange,
    press: QuickSelectPress,
) {
    if (!press.open()) {
        trackPress(down) {}
        return
    }
    var finished = false
    val outcome =
        try {
            trackPress(down, press::highlightAt).also { finished = true }
        } finally {
            if (!finished) press.callbacks.onCancel()
        }
    press.finish(outcome)
}

/** Follows [down]'s pointer until it lifts, reporting each pressed position to [onMove]. */
private suspend fun AwaitPointerEventScope.trackPress(
    down: PointerInputChange,
    onMove: (Offset) -> Unit,
): PressOutcome {
    var travelled = false
    while (true) {
        val change = awaitTrackedChange(down.id) ?: return PressOutcome.Cancel
        travelled = travelled || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
        if (!change.pressed) {
            val held = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
            return if (travelled || held) PressOutcome.Drag else PressOutcome.Tap
        }
        onMove(change.position)
    }
}

/**
 * The next change of [pointer] with every change of the event consumed, or null when the event lost the pointer
 * or is Compose's synthetic cancellation: a lift that arrives already consumed.
 */
private suspend fun AwaitPointerEventScope.awaitTrackedChange(pointer: PointerId): PointerInputChange? {
    val event = awaitPointerEvent()
    val change = event.changes.firstOrNull { it.id == pointer }
    val cancelled = change?.let { !it.pressed && it.isConsumed } ?: true
    event.changes.forEach { it.consume() }
    return change.takeUnless { cancelled }
}

private enum class PressOutcome { Drag, Tap, Cancel }

/** One press with the menu open; it sends a highlight only when the item under the pointer changes. */
private class QuickSelectPress(
    val callbacks: EditorQuickSelectCallbacks,
    private val session: State<QuickSelectGestureSession>,
    private val onTapMode: (Boolean) -> Unit,
    private val tap: QuickSelectControlTap,
) {
    private var highlighted: QuickSelectItem? = null

    /** Whether the menu is open for this press: already in tap mode, or opened now. */
    fun open(): Boolean = tap == QuickSelectControlTap.Cancel || callbacks.onOpen().quickSelection.menu != null

    fun highlightAt(pointer: Offset) {
        val item = session.value.placement?.itemAt(pointer)
        if (item != highlighted) {
            highlighted = item
            callbacks.onHighlight(item)
        }
    }

    fun finish(outcome: PressOutcome) {
        when (outcome) {
            PressOutcome.Drag -> {
                onTapMode(false)
                callbacks.onConfirm()
            }

            PressOutcome.Tap -> {
                callbacks.applyControlTap(tap, onTapMode)
            }

            PressOutcome.Cancel -> {
                onTapMode(false)
                callbacks.onCancel()
            }
        }
    }
}
