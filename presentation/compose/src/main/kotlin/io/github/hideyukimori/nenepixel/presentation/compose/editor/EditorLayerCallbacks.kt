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
 * source. Each call publishes the resulting render state and returns it with the application result.
 */
internal class EditorLayerCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onSelect(layerId: LayerId): EditorLayerOutcome {
        val result = runtime.reduce(WorkspaceAction.SelectLayer(layerId))
        return EditorLayerOutcome.Reduced(publish(adapter.renderState), result)
    }

    fun onSetVisibility(
        layerId: LayerId,
        visibility: LayerVisibility,
    ): EditorLayerOutcome = execute(SetLayerVisibilityCommand.create(runtime.captureSource(), layerId, visibility))

    /** Adds a layer above the active layer, read from the runtime at the time of the call. */
    fun onAdd(): EditorLayerOutcome {
        val activeLayerId = runtime.state.workspaceState.activeLayerId
        return execute(AddLayerCommand.create(runtime.captureSource(), activeLayerId))
    }

    fun onDelete(layerId: LayerId): EditorLayerOutcome =
        execute(DeleteLayerCommand.create(runtime.captureSource(), layerId))

    /** Moves [layerId] to [toPosition], counted from the bottom (0) as `MoveLayerCommand` defines. */
    fun onMove(
        layerId: LayerId,
        toPosition: Int,
    ): EditorLayerOutcome = execute(MoveLayerCommand.create(runtime.captureSource(), layerId, toPosition))

    fun onRename(
        layerId: LayerId,
        name: LayerName,
    ): EditorLayerOutcome = execute(RenameLayerCommand.create(runtime.captureSource(), layerId, name))

    private fun execute(command: DocumentCommand): EditorLayerOutcome {
        val result = runtime.execute(command)
        return EditorLayerOutcome.Executed(publish(adapter.renderState), result)
    }
}
