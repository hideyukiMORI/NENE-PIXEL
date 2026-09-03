package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public class EditorController private constructor(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
) {
    public val renderState: EditorRenderState
        get() = adapter.renderState

    public val callbacks: EditorCallbacks =
        EditorCallbacks(
            pointerDown = ::pointerDown,
            pointerMove = ::pointerMove,
            pointerEnd = ::pointerEnd,
            pointerCancel = ::pointerCancel,
            viewportStarted = ::viewportStarted,
            viewportTransformed = ::viewportTransformed,
            undo = adapter::undo,
            redo = adapter::redo,
            createNewDocument = adapter::createNewDocument,
        )

    internal val documentState: DocumentState
        get() = runtime.state.documentState

    internal val workspaceState: WorkspaceState
        get() = runtime.state.workspaceState

    internal fun pointerDown(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        withMappedPoint(surface, point, outsideIsCancellation = false) { position ->
            adapter.reduce(
                WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position),
            )
        }

    internal fun pointerMove(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        withMappedPoint(surface, point, outsideIsCancellation = true) { position ->
            adapter.reduce(WorkspaceAction.ExtendGesturePreview(position))
        }

    internal fun pointerEnd(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        withMappedPoint(surface, point, outsideIsCancellation = true, action = adapter::finishGesture)

    internal fun pointerCancel(): PointerInputAcknowledgement =
        if (runtime.state.workspaceState.preview == null) {
            adapter.ignored()
        } else {
            adapter.reduce(WorkspaceAction.CancelGesturePreview)
        }

    internal fun viewportStarted(surface: ViewportSurface): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> adapter.reduce(WorkspaceAction.SetViewport(transform.value.viewport))
            is ViewportValueResult.Rejected -> adapter.rejected()
        }

    internal fun viewportTransformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> applyViewportGesture(transform.value, gesture)
            is ViewportValueResult.Rejected -> adapter.rejected()
        }

    private fun applyViewportGesture(
        transform: ViewportTransform,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement =
        when (val nextViewport = transform.apply(gesture)) {
            is ViewportValueResult.Created -> adapter.reduce(WorkspaceAction.SetViewport(nextViewport.value))
            is ViewportValueResult.Rejected -> adapter.rejected()
        }

    private fun withMappedPoint(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
        outsideIsCancellation: Boolean,
        action: (PixelPosition) -> PointerInputAcknowledgement,
    ): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> {
                mapWithNormalizedViewport(transform.value, point, outsideIsCancellation, action)
            }

            is ViewportValueResult.Rejected -> {
                adapter.rejected()
            }
        }

    private fun mapWithNormalizedViewport(
        transform: ViewportTransform,
        point: ViewportSurfacePoint,
        outsideIsCancellation: Boolean,
        action: (PixelPosition) -> PointerInputAcknowledgement,
    ): PointerInputAcknowledgement {
        if (transform.viewport != runtime.state.workspaceState.viewport) {
            val normalization = adapter.reduce(WorkspaceAction.SetViewport(transform.viewport))
            if (
                normalization is PointerInputAcknowledgement.Cancelled ||
                normalization is PointerInputAcknowledgement.Rejected
            ) {
                return normalization
            }
        }
        return when (val mapping = transform.toPixelPosition(point)) {
            is ViewportMappingResult.Mapped -> action(mapping.value)

            ViewportMappingResult.OutsideCanvas,
            ViewportMappingResult.OutsideSurface,
            -> if (outsideIsCancellation) pointerCancel() else adapter.ignored()
        }
    }

    private fun createTransform(surface: ViewportSurface): ViewportValueResult<ViewportTransform> {
        val state = runtime.state
        return ViewportTransform.create(
            canvas = state.documentState.size,
            surface = surface,
            viewport = state.workspaceState.viewport,
        )
    }

    public companion object {
        public fun create(runtime: EditorRuntime): EditorController =
            EditorController(runtime, EditorRuntimeAdapter(runtime))
    }
}
