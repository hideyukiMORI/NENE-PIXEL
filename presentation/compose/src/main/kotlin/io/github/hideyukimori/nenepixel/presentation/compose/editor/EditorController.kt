package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class EditorController private constructor(
    internal val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
) {
    private val mapping: ViewportGestureMapping = ViewportGestureMapping(runtime, adapter)
    private val mutableRenderState: MutableStateFlow<EditorRenderState> = MutableStateFlow(adapter.renderState)

    public val renderState: EditorRenderState
        get() = mutableRenderState.value

    public val renderStates: StateFlow<EditorRenderState> = mutableRenderState.asStateFlow()

    public val callbacks: EditorCallbacks =
        EditorCallbacks(
            pointerDown = ::pointerDown,
            pointerMove = ::pointerMove,
            pointerEnd = ::pointerEnd,
            pointerCancel = ::pointerCancel,
            viewportStarted = ::viewportStarted,
            viewportTransformed = ::viewportTransformed,
            undo = { publish(adapter.undo()) },
            redo = { publish(adapter.redo()) },
            selectTool = { tool -> publish(adapter.reduce(WorkspaceAction.SelectTool(tool)).renderState) },
            selectPaletteEntry = { index ->
                publish(adapter.reduce(WorkspaceAction.SelectPaletteEntry(index)).renderState)
            },
            setAppearance = { appearance ->
                publish(adapter.reduce(WorkspaceAction.SetAppearance(appearance)).renderState)
            },
        )

    public fun synchronizeWithRuntime() {
        publish(adapter.renderState)
    }

    internal val documentState: DocumentState
        get() = runtime.state.documentState

    internal val workspaceState: WorkspaceState
        get() = runtime.state.workspaceState

    internal fun pointerDown(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        acknowledge(
            mapping.withMappedPoint(surface, point, adapter::ignored) { position ->
                adapter.reduce(
                    WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position),
                )
            },
        )

    internal fun pointerMove(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        acknowledge(
            mapping.withMappedPoint(surface, point, ::pointerCancel) { position ->
                adapter.reduce(WorkspaceAction.ExtendGesturePreview(position))
            },
        )

    internal fun pointerEnd(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        acknowledge(mapping.withMappedPoint(surface, point, ::pointerCancel, adapter::finishGesture))

    internal fun pointerCancel(): PointerInputAcknowledgement =
        acknowledge(
            if (runtime.state.workspaceState.preview == null) {
                adapter.ignored()
            } else {
                adapter.reduce(WorkspaceAction.CancelGesturePreview)
            },
        )

    internal fun viewportStarted(surface: ViewportSurface): PointerInputAcknowledgement =
        acknowledge(
            when (val transform = mapping.createTransform(surface)) {
                is ViewportValueResult.Created -> adapter.reduce(WorkspaceAction.SetViewport(transform.value.viewport))
                is ViewportValueResult.Rejected -> adapter.rejected()
            },
        )

    internal fun viewportTransformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement =
        acknowledge(
            when (val transform = mapping.createTransform(surface)) {
                is ViewportValueResult.Created -> mapping.applyGesture(transform.value, gesture)
                is ViewportValueResult.Rejected -> adapter.rejected()
            },
        )

    private fun acknowledge(acknowledgement: PointerInputAcknowledgement): PointerInputAcknowledgement {
        publish(acknowledgement.renderState)
        return acknowledgement
    }

    private fun publish(state: EditorRenderState): EditorRenderState {
        mutableRenderState.value = state
        return state
    }

    public companion object {
        public fun create(runtime: EditorRuntime): EditorController =
            EditorController(runtime, EditorRuntimeAdapter(runtime))
    }
}
