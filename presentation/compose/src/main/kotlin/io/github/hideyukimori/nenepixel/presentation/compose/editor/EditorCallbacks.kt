package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint

public class EditorCallbacks internal constructor(
    private val pointerDown: (ViewportSurface, ViewportSurfacePoint) -> PointerInputAcknowledgement,
    private val pointerMove: (ViewportSurface, ViewportSurfacePoint) -> PointerInputAcknowledgement,
    private val pointerEnd: (ViewportSurface, ViewportSurfacePoint) -> PointerInputAcknowledgement,
    private val pointerCancel: () -> PointerInputAcknowledgement,
    private val viewportStarted: (ViewportSurface) -> PointerInputAcknowledgement,
    private val viewportTransformed: (ViewportSurface, ViewportGesture) -> PointerInputAcknowledgement,
    private val undo: () -> EditorRenderState,
    private val redo: () -> EditorRenderState,
    private val createNewDocument: (String, String) -> NewDocumentSubmission,
) {
    internal fun onPointerDown(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = pointerDown(surface, point)

    internal fun onPointerMove(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = pointerMove(surface, point)

    internal fun onPointerEnd(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = pointerEnd(surface, point)

    internal fun onPointerCancel(): PointerInputAcknowledgement = pointerCancel()

    internal fun onViewportStarted(surface: ViewportSurface): PointerInputAcknowledgement = viewportStarted(surface)

    internal fun onViewportTransformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement = viewportTransformed(surface, gesture)

    public fun onUndo(): EditorRenderState = undo()

    public fun onRedo(): EditorRenderState = redo()

    internal fun onCreateNewDocument(
        rawWidth: String,
        rawHeight: String,
    ): NewDocumentSubmission = createNewDocument(rawWidth, rawHeight)
}
