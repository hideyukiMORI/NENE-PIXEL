package io.github.hideyukimori.nenepixel.presentation.compose.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorCallbacks
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorRenderState

internal fun Modifier.fixedCanvasPointerInput(
    canvas: CanvasSize,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
): Modifier =
    pointerInput(canvas, callbacks) {
        val translator = FixedCanvasTouchTranslator.create()
        awaitEachGesture {
            runGesture(canvas, translator, callbacks, onRenderStateChanged)
        }
    }

private suspend fun AwaitPointerEventScope.runGesture(
    canvas: CanvasSize,
    translator: FixedCanvasTouchTranslator,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    val start = translator.translate(down.position, size.toSize(), canvas)
    if (start !is TouchTranslation.Mapped) return
    val gesture = PointerGesture(callbacks, onRenderStateChanged)
    try {
        gesture.start(start.position)
        down.consume()
        trackGesture(down.id, canvas, translator, gesture)
    } finally {
        gesture.cancel()
    }
}

private suspend fun AwaitPointerEventScope.trackGesture(
    pointerId: PointerId,
    canvas: CanvasSize,
    translator: FixedCanvasTouchTranslator,
    gesture: PointerGesture,
) {
    while (gesture.active) {
        when (val step = awaitPointerEvent().classify(pointerId, size.toSize(), canvas, translator)) {
            is PointerStep.Move -> {
                gesture.move(step.position)
                step.change.consume()
            }

            is PointerStep.End -> {
                gesture.end(step.position)
                step.change.consume()
            }

            PointerStep.Cancel -> {
                gesture.cancel()
            }
        }
    }
}

private fun PointerEvent.classify(
    pointerId: PointerId,
    surface: Size,
    canvas: CanvasSize,
    translator: FixedCanvasTouchTranslator,
): PointerStep {
    val tracked = changes.firstOrNull { change -> change.id == pointerId }
    return when {
        tracked == null -> PointerStep.Cancel
        hasAdditionalPressedPointer(pointerId) -> PointerStep.Cancel
        else -> tracked.classify(surface, canvas, translator)
    }
}

private fun PointerInputChange.classify(
    surface: Size,
    canvas: CanvasSize,
    translator: FixedCanvasTouchTranslator,
): PointerStep {
    val translation = translator.translate(position, surface, canvas)
    return when {
        changedToUpIgnoreConsumed() -> translation.toEndStep(this)
        !pressed -> PointerStep.Cancel
        translation is TouchTranslation.Mapped -> PointerStep.Move(this, translation.position)
        else -> PointerStep.Cancel
    }
}

private fun PointerEvent.hasAdditionalPressedPointer(pointerId: PointerId): Boolean =
    changes.any { change -> change.id != pointerId && change.pressed }

private fun TouchTranslation.toEndStep(change: PointerInputChange): PointerStep =
    when (this) {
        is TouchTranslation.Mapped -> PointerStep.End(change, position)
        TouchTranslation.Outside -> PointerStep.Cancel
    }

private class PointerGesture(
    private val callbacks: EditorCallbacks,
    private val onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    var active: Boolean = false
        private set

    fun start(position: PixelPosition) {
        onRenderStateChanged(callbacks.onPointerDown(position))
        active = true
    }

    fun move(position: PixelPosition) {
        onRenderStateChanged(callbacks.onPointerMove(position))
    }

    fun end(position: PixelPosition) {
        onRenderStateChanged(callbacks.onPointerEnd(position))
        active = false
    }

    fun cancel() {
        if (active) {
            onRenderStateChanged(callbacks.onPointerCancel())
            active = false
        }
    }
}

private sealed interface PointerStep {
    data class Move(
        val change: PointerInputChange,
        val position: PixelPosition,
    ) : PointerStep

    data class End(
        val change: PointerInputChange,
        val position: PixelPosition,
    ) : PointerStep

    data object Cancel : PointerStep
}
