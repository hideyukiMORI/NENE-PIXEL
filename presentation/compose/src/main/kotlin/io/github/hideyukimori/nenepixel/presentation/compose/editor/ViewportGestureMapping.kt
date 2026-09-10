package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

internal class ViewportGestureMapping(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
) {
    fun createTransform(surface: ViewportSurface): ViewportValueResult<ViewportTransform> {
        val state = runtime.state
        return ViewportTransform.create(
            canvas = state.documentState.size,
            surface = surface,
            viewport = state.workspaceState.viewport,
        )
    }

    fun applyGesture(
        transform: ViewportTransform,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement =
        when (val nextViewport = transform.apply(gesture)) {
            is ViewportValueResult.Created -> adapter.reduce(WorkspaceAction.SetViewport(nextViewport.value))
            is ViewportValueResult.Rejected -> adapter.rejected()
        }

    fun withMappedPoint(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
        outside: () -> PointerInputAcknowledgement,
        action: (PixelPosition) -> PointerInputAcknowledgement,
    ): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> {
                mapWithNormalizedViewport(transform.value, point, outside, action)
            }

            is ViewportValueResult.Rejected -> {
                adapter.rejected()
            }
        }

    private fun mapWithNormalizedViewport(
        transform: ViewportTransform,
        point: ViewportSurfacePoint,
        outside: () -> PointerInputAcknowledgement,
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
            -> outside()
        }
    }
}
