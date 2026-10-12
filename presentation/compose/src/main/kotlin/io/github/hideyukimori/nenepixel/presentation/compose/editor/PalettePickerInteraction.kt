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
import androidx.compose.ui.unit.IntSize

/** Updated callbacks keep a live gesture stable while each new canonical colour is published. */
internal data class PalettePickerInteraction(
    val enabled: Boolean,
    val callbacks: PaletteColorEditCallbacks,
    val changeAt: (Offset, IntSize) -> Unit,
)

/** Consumes a tap/drag as one begin-update-finish; system cancellation and disposal restore its start. */
internal fun Modifier.palettePickerGesture(interaction: State<PalettePickerInteraction>): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (interaction.value.enabled && interaction.value.callbacks.begin()) {
                down.consume()
                val press = PalettePickerPress(interaction, size)
                editPaletteColor(down, press)
            }
        }
    }

private class PalettePickerPress(
    private val interaction: State<PalettePickerInteraction>,
    private val size: IntSize,
) {
    fun update(position: Offset) {
        interaction.value.changeAt(position, size)
    }

    fun finish() {
        interaction.value.callbacks.finish()
    }

    fun cancel() {
        interaction.value.callbacks.cancel()
    }
}

/** Owns the starting update, final release and cancellation of exactly one accepted press. */
private suspend fun AwaitPointerEventScope.editPaletteColor(
    down: PointerInputChange,
    press: PalettePickerPress,
) {
    var finished = false
    try {
        press.update(down.position)
        finished = trackPalettePointer(down.id, press)
        if (finished) press.finish()
    } finally {
        if (!finished) press.cancel()
    }
}

private suspend fun AwaitPointerEventScope.trackPalettePointer(
    pointer: PointerId,
    press: PalettePickerPress,
): Boolean {
    while (true) {
        val change = palettePointerChange(pointer) ?: return false
        press.update(change.position)
        if (!change.pressed) return true
    }
}

private suspend fun AwaitPointerEventScope.palettePointerChange(pointer: PointerId): PointerInputChange? {
    val event = awaitPointerEvent()
    val change = event.changes.firstOrNull { it.id == pointer }
    val cancelled = change?.let { !it.pressed && it.isConsumed } ?: true
    event.changes.forEach { it.consume() }
    return change.takeUnless { cancelled }
}
