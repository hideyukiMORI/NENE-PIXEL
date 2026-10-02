package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayAdjustment
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult

/**
 * The canvas pointer route while [CanvasPointerIntent.AdjustUnderlay] holds (ADR 0032). It remembers the last surface
 * point of a one-pointer drag and reduces one `SetReferenceUnderlay` per move; it never starts a stroke and never
 * reduces `SetViewport`. The arithmetic is [UnderlayAdjustment] under the current viewport transform.
 */
internal class UnderlayAdjustPointer(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val mapping: ViewportGestureMapping,
) {
    private var last: ViewportSurfacePoint? = null

    /**
     * Whether this input belongs to the adjust route. When the mode has ended (the underlay is gone, hidden or
     * resting), the remembered point is dropped and the caller takes its normal route.
     */
    fun claims(): Boolean {
        val adjusting = runtime.state.workspaceState.canvasPointerIntent == CanvasPointerIntent.AdjustUnderlay
        if (!adjusting) {
            last = null
        }
        return adjusting
    }

    /**
     * Remembers [point], inside the document or not, and changes no state. The acknowledgement is `Accepted`, the
     * only result on which the canvas pointer session keeps delivering the moves of this pointer.
     */
    fun down(point: ViewportSurfacePoint): PointerInputAcknowledgement {
        last = point
        return PointerInputAcknowledgement.Accepted(adapter.renderState)
    }

    fun move(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement {
        val from = last
        last = point
        return if (from == null) {
            adapter.ignored()
        } else {
            reduceWith(surface) { underlay, transform -> UnderlayAdjustment.moved(underlay, from, point, transform) }
        }
    }

    fun end(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement {
        val acknowledgement = move(surface, point)
        last = null
        return acknowledgement
    }

    /** A cancel and a second pointer both drop the remembered point; the underlay stays where it was moved. */
    fun forget(): PointerInputAcknowledgement {
        last = null
        return adapter.ignored()
    }

    fun transformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement =
        reduceWith(surface) { underlay, transform -> UnderlayAdjustment.transformed(underlay, gesture, transform) }

    private fun reduceWith(
        surface: ViewportSurface,
        adjust: (ReferenceUnderlay, ViewportTransform) -> ReferenceUnderlay,
    ): PointerInputAcknowledgement {
        val underlay = runtime.state.workspaceState.underlay ?: return adapter.ignored()
        return when (val transform = mapping.createTransform(surface)) {
            is ViewportValueResult.Created -> {
                adapter.reduce(WorkspaceAction.SetReferenceUnderlay(adjust(underlay, transform.value)))
            }

            is ViewportValueResult.Rejected -> {
                adapter.rejected()
            }
        }
    }
}
