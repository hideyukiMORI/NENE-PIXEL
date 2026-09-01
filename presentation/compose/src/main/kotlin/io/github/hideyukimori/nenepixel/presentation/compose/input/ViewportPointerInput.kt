package io.github.hideyukimori.nenepixel.presentation.compose.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorCallbacks
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorRenderState
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PointerInputAcknowledgement

internal fun Modifier.viewportPointerInput(
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
): Modifier =
    pointerInput(callbacks) {
        awaitEachGesture {
            runViewportGesture(callbacks, onRenderStateChanged)
        }
    }

private suspend fun AwaitPointerEventScope.runViewportGesture(
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    val firstDown = awaitFirstDown(requireUnconsumed = false)
    val session = ViewportPointerSession(callbacks, onRenderStateChanged)
    try {
        val surface = validatedSurface() ?: return
        val point = firstDown.validatedPoint() ?: return
        session.start(firstDown.id, surface, point)
        firstDown.consume()
        while (session.isActive) {
            val event = awaitPointerEvent()
            session.handle(event, validatedSurface())
        }
    } finally {
        session.cancel()
    }
}

private class ViewportPointerSession(
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    private val dispatcher = PointerCallbackDispatcher(callbacks, onRenderStateChanged)
    private var phase: PointerPhase = PointerPhase.Suppressed

    val isActive: Boolean
        get() = phase != PointerPhase.Finished

    fun start(
        pointerId: PointerId,
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ) {
        phase =
            when (dispatcher.pointerDown(surface, point)) {
                is PointerInputAcknowledgement.Accepted -> PointerPhase.Drawing(pointerId, surface)

                is PointerInputAcknowledgement.Cancelled,
                is PointerInputAcknowledgement.Ignored,
                is PointerInputAcknowledgement.Rejected,
                -> PointerPhase.Suppressed
            }
    }

    fun handle(
        event: PointerEvent,
        surface: ViewportSurface?,
    ) {
        if (surface == null) {
            suppress()
            return
        }
        when (val current = phase) {
            is PointerPhase.Drawing -> handleDrawing(current, event, surface)
            is PointerPhase.Transforming -> handleTransforming(current, event, surface)
            PointerPhase.Suppressed -> handleSuppressed(event)
            PointerPhase.Finished -> Unit
        }
        event.consumeHandledChanges()
    }

    fun cancel() {
        dispatcher.pointerCancel()
        phase = PointerPhase.Finished
    }

    private fun handleDrawing(
        current: PointerPhase.Drawing,
        event: PointerEvent,
        surface: ViewportSurface,
    ) {
        val pressed = event.pressedChanges()
        when (
            classifyPointerFrame(
                PointerInteractionMode.Drawing,
                event.facts(current.pointerId, surface != current.surface),
            )
        ) {
            PointerDirective.Draw,
            PointerDirective.EndDraw,
            -> handleTrackedPointer(event, current, surface)

            PointerDirective.CancelDrawAndBeginTransform -> beginTransform(pressed, surface)

            PointerDirective.Suppress -> suppress()

            PointerDirective.ApplyTransform,
            PointerDirective.RebaseTransform,
            PointerDirective.Wait,
            PointerDirective.Finish,
            -> error("Drawing classifier returned an invalid directive.")
        }
    }

    private fun handleTrackedPointer(
        event: PointerEvent,
        current: PointerPhase.Drawing,
        surface: ViewportSurface,
    ) {
        val tracked = event.changes.firstOrNull { change -> change.id == current.pointerId }
        val point = tracked?.validatedPoint()
        when {
            tracked == null || point == null -> {
                suppress()
            }

            tracked.changedToUpIgnoreConsumed() -> {
                dispatcher.pointerEnd(surface, point)
                phase = PointerPhase.Finished
            }

            tracked.pressed -> {
                when (dispatcher.pointerMove(surface, point)) {
                    is PointerInputAcknowledgement.Accepted,
                    is PointerInputAcknowledgement.Ignored,
                    -> Unit

                    is PointerInputAcknowledgement.Cancelled,
                    is PointerInputAcknowledgement.Rejected,
                    -> phase = PointerPhase.Suppressed
                }
            }

            else -> {
                suppress()
            }
        }
    }

    private fun beginTransform(
        pressed: List<PointerInputChange>,
        surface: ViewportSurface,
    ) {
        val acknowledgement = dispatcher.viewportStarted(surface)
        phase =
            when (acknowledgement) {
                is PointerInputAcknowledgement.Rejected -> PointerPhase.Suppressed

                is PointerInputAcknowledgement.Accepted,
                is PointerInputAcknowledgement.Cancelled,
                is PointerInputAcknowledgement.Ignored,
                -> PointerPhase.Transforming(pressed.toBaseline(surface))
            }
    }

    private fun handleTransforming(
        current: PointerPhase.Transforming,
        event: PointerEvent,
        surface: ViewportSurface,
    ) {
        val pressed = event.pressedChanges()
        val nextBaseline = pressed.toBaselineOrNull(surface)
        if (pressed.size == POINTER_TRANSFORM_COUNT && nextBaseline == null) {
            suppress()
            return
        }
        when (event.transformDirective(current.baseline, nextBaseline, surface)) {
            PointerDirective.Finish -> {
                phase = PointerPhase.Finished
            }

            PointerDirective.Suppress -> {
                suppress()
            }

            PointerDirective.Wait -> {
                phase = PointerPhase.Transforming(null)
            }

            PointerDirective.RebaseTransform -> {
                phase = rebaseTransform(current.baseline, requireNotNull(nextBaseline))
            }

            PointerDirective.ApplyTransform -> {
                phase = updateTransform(requireNotNull(current.baseline), requireNotNull(nextBaseline))
            }

            PointerDirective.Draw,
            PointerDirective.EndDraw,
            PointerDirective.CancelDrawAndBeginTransform,
            -> {
                error("Transform classifier returned an invalid directive.")
            }
        }
    }

    private fun rebaseTransform(
        previous: TransformBaseline?,
        current: TransformBaseline,
    ): PointerPhase {
        if (previous != null && previous.surface != current.surface) {
            val acknowledgement = dispatcher.viewportStarted(current.surface)
            if (acknowledgement is PointerInputAcknowledgement.Rejected) {
                return PointerPhase.Suppressed
            }
        }
        return PointerPhase.Transforming(current)
    }

    private fun updateTransform(
        previous: TransformBaseline,
        current: TransformBaseline,
    ): PointerPhase {
        val gesture =
            ViewportGesture.create(
                previous.first.point,
                previous.second.point,
                current.first.point,
                current.second.point,
            )
        return when (dispatcher.viewportTransformed(current.surface, gesture)) {
            is PointerInputAcknowledgement.Rejected -> PointerPhase.Suppressed

            is PointerInputAcknowledgement.Accepted,
            is PointerInputAcknowledgement.Cancelled,
            is PointerInputAcknowledgement.Ignored,
            -> PointerPhase.Transforming(current)
        }
    }

    private fun handleSuppressed(event: PointerEvent) {
        when (classifyPointerFrame(PointerInteractionMode.Suppressed, event.facts())) {
            PointerDirective.Finish -> phase = PointerPhase.Finished
            PointerDirective.Wait -> Unit
            else -> error("Suppressed classifier returned an invalid directive.")
        }
    }

    private fun suppress() {
        dispatcher.pointerCancel()
        phase = PointerPhase.Suppressed
    }
}

private fun PointerEvent.transformDirective(
    previous: TransformBaseline?,
    current: TransformBaseline?,
    surface: ViewportSurface,
): PointerDirective {
    val sameMembership = previous != null && current != null && previous.sameMembership(current)
    val surfaceChanged = previous?.surface?.let { baselineSurface -> baselineSurface != surface } ?: false
    return classifyPointerFrame(
        PointerInteractionMode.Transforming,
        facts(
            surfaceChanged = surfaceChanged,
            sameTransformMembership = sameMembership,
        ),
    )
}

private class PointerCallbackDispatcher(
    private val callbacks: EditorCallbacks,
    private val onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    fun pointerDown(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = publish(callbacks.onPointerDown(surface, point))

    fun pointerMove(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = publish(callbacks.onPointerMove(surface, point))

    fun pointerEnd(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = publish(callbacks.onPointerEnd(surface, point))

    fun pointerCancel(): PointerInputAcknowledgement = publish(callbacks.onPointerCancel())

    fun viewportStarted(surface: ViewportSurface): PointerInputAcknowledgement =
        publish(callbacks.onViewportStarted(surface))

    fun viewportTransformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement = publish(callbacks.onViewportTransformed(surface, gesture))

    private fun publish(acknowledgement: PointerInputAcknowledgement): PointerInputAcknowledgement {
        onRenderStateChanged(acknowledgement.renderState)
        return acknowledgement
    }
}

private sealed interface PointerPhase {
    data class Drawing(
        val pointerId: PointerId,
        val surface: ViewportSurface,
    ) : PointerPhase

    data class Transforming(
        val baseline: TransformBaseline?,
    ) : PointerPhase

    data object Suppressed : PointerPhase

    data object Finished : PointerPhase
}

private data class TransformBaseline(
    val surface: ViewportSurface,
    val first: TrackedPoint,
    val second: TrackedPoint,
) {
    fun sameMembership(other: TransformBaseline): Boolean =
        surface == other.surface &&
            first.pointerId == other.first.pointerId &&
            second.pointerId == other.second.pointerId
}

private data class TrackedPoint(
    val pointerId: PointerId,
    val point: ViewportSurfacePoint,
)

private fun List<PointerInputChange>.toBaseline(surface: ViewportSurface): TransformBaseline =
    requireNotNull(toBaselineOrNull(surface)) { "Pressed pointers must contain two finite points." }

private fun List<PointerInputChange>.toBaselineOrNull(surface: ViewportSurface): TransformBaseline? {
    val points =
        mapNotNull { change -> change.validatedPoint()?.let { point -> TrackedPoint(change.id, point) } }
            .sortedBy { tracked -> tracked.pointerId.value }
    return if (points.size == POINTER_TRANSFORM_COUNT) {
        TransformBaseline(surface, points[0], points[1])
    } else {
        null
    }
}

private fun AwaitPointerEventScope.validatedSurface(): ViewportSurface? =
    when (
        val result =
            ViewportSurface.create(
                widthPixels = size.width,
                heightPixels = size.height,
                pixelsPerDp = density.toDouble(),
            )
    ) {
        is ViewportValueResult.Created -> result.value
        is ViewportValueResult.Rejected -> null
    }

private fun PointerInputChange.validatedPoint(): ViewportSurfacePoint? =
    when (val result = ViewportSurfacePoint.create(position.x.toDouble(), position.y.toDouble())) {
        is ViewportValueResult.Created -> result.value
        is ViewportValueResult.Rejected -> null
    }

private fun PointerEvent.pressedChanges(): List<PointerInputChange> = changes.filter(PointerInputChange::pressed)

private fun PointerEvent.facts(
    trackedDrawingId: PointerId? = null,
    surfaceChanged: Boolean = false,
    sameTransformMembership: Boolean = false,
): PointerFrameFacts =
    PointerFrameFacts(
        pressedIds = pressedChanges().mapTo(mutableSetOf()) { change -> change.id.value },
        releasedIds =
            changes
                .filter(PointerInputChange::changedToUpIgnoreConsumed)
                .mapTo(mutableSetOf()) { change -> change.id.value },
        trackedDrawingId = trackedDrawingId?.value,
        surfaceChanged = surfaceChanged,
        sameTransformMembership = sameTransformMembership,
    )

private fun PointerEvent.consumeHandledChanges() {
    changes
        .filter { change -> change.pressed || change.changedToUpIgnoreConsumed() }
        .forEach { change -> change.consume() }
}

private const val POINTER_TRANSFORM_COUNT: Int = 2
