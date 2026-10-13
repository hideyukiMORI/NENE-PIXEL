package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public class EditorCallbacks internal constructor(
    private val controller: EditorController,
    private val adapter: EditorRuntimeAdapter,
) {
    internal val palette: EditorPaletteCallbacks =
        EditorPaletteCallbacks(controller.runtime, adapter, controller::publish)
    internal val quickSelect: EditorQuickSelectCallbacks = EditorQuickSelectCallbacks(adapter, controller::publish)
    internal val layers: EditorLayerCallbacks = EditorLayerCallbacks(controller.runtime, adapter, controller::publish)
    internal val underlay: EditorUnderlayCallbacks =
        EditorUnderlayCallbacks(controller.runtime, adapter, controller::publish)
    internal val rasterImport: EditorImportCallbacks =
        EditorImportCallbacks(controller.runtime, adapter, controller::publish)
    internal val selection: EditorSelectionCallbacks =
        EditorSelectionCallbacks(controller.runtime, adapter, controller::publish)
    internal val frames: EditorFrameCallbacks = EditorFrameCallbacks(controller.runtime, adapter, controller::publish)
    internal val playback: EditorPlaybackCallbacks get() = frames.playback
    internal val viewport: EditorViewportCallbacks =
        EditorViewportCallbacks(controller::viewportStarted, controller::viewportTransformed)

    internal fun onPointerDown(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = controller.pointerDown(surface, point)

    internal fun onPointerMove(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = controller.pointerMove(surface, point)

    internal fun onPointerEnd(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement = controller.pointerEnd(surface, point)

    internal fun onPointerCancel(): PointerInputAcknowledgement = controller.pointerCancel()

    public fun onUndo(): EditorRenderState = controller.publish(adapter.undo())

    public fun onRedo(): EditorRenderState = controller.publish(adapter.redo())

    internal fun onSelectTool(tool: DrawingTool): EditorRenderState =
        controller.editingAction(WorkspaceAction.SelectTool(tool))

    internal fun onSelectPaletteEntry(index: PaletteIndex): EditorRenderState =
        controller.editingAction(WorkspaceAction.SelectPaletteEntry(index))

    internal fun onSetAppearance(appearance: EditorAppearance): EditorRenderState =
        controller.publish(adapter.reduce(WorkspaceAction.SetAppearance(appearance)).renderState)

    internal fun onSetActualSizeWindow(window: ActualSizeWindow): EditorRenderState =
        controller.publish(adapter.reduce(WorkspaceAction.SetActualSizeWindow(window)).renderState)
}
