package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.DeleteLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.MoveLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RenameLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/**
 * The layer-panel route (#144, ADR 0030), reached through [EditorCallbacks.layers]. Selection reduces
 * `WorkspaceAction.SelectLayer`; every other call executes one layer `DocumentCommand` against a freshly captured
 * source. Each call records its result in the adapter's layer notice (#144 U6) and publishes the resulting render
 * state, which carries the notice.
 */
internal class EditorLayerCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onSelect(layerId: LayerId): EditorRenderState {
        val result = runtime.reduce(WorkspaceAction.SelectLayer(layerId))
        adapter.layerNotices.recordSelection(result, runtime.state.documentState)
        return publish(adapter.renderState)
    }

    fun onSetVisibility(
        layerId: LayerId,
        visibility: LayerVisibility,
    ): EditorRenderState = execute(SetLayerVisibilityCommand.create(runtime.captureSource(), layerId, visibility))

    /** Adds a layer above the active layer, read from the runtime at the time of the call. */
    fun onAdd(): EditorRenderState {
        val activeLayerId = runtime.state.workspaceState.activeLayerId
        return execute(AddLayerCommand.create(runtime.captureSource(), activeLayerId))
    }

    fun onDelete(layerId: LayerId): EditorRenderState =
        execute(DeleteLayerCommand.create(runtime.captureSource(), layerId))

    /** Moves [layerId] to [toPosition], counted from the bottom (0) as `MoveLayerCommand` defines. */
    fun onMove(
        layerId: LayerId,
        toPosition: Int,
    ): EditorRenderState = execute(MoveLayerCommand.create(runtime.captureSource(), layerId, toPosition))

    fun onRename(
        layerId: LayerId,
        name: LayerName,
    ): EditorRenderState = execute(RenameLayerCommand.create(runtime.captureSource(), layerId, name))

    /** The UI has finished showing notice [serial] (timed out, dismissed or acted on); it does not come back. */
    fun onNoticeSettled(serial: Int): EditorRenderState =
        if (adapter.layerNotices.settle(serial)) publish(adapter.renderState) else adapter.renderState

    private fun execute(command: DocumentCommand): EditorRenderState {
        val result = runtime.execute(command)
        adapter.layerNotices.recordCommand(result, command is DeleteLayerCommand, runtime.state.documentState)
        return publish(adapter.renderState)
    }
}
