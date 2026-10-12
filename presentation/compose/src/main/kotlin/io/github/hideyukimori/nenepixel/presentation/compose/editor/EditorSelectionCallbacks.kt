package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.SelectionApplyResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode

/** Issue #211: selection controls share the canonical reducer/runtime; they own no draft geometry or pixels. */
internal class EditorSelectionCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onBegin(layerId: LayerId): WorkspaceReductionResult {
        val result = runtime.selectionOperations.beginSelection(layerId)
        adapter.layerNotices.recordSelection(result, runtime.state.documentState)
        adapter.selectionNotice = null
        publish(adapter.renderState)
        return result
    }

    fun onMode(mode: PixelTransferMode): EditorRenderState =
        publish(adapter.reduce(WorkspaceAction.SetSelectionMode(mode)).renderState)

    fun onCancel(): EditorRenderState {
        adapter.selectionNotice = null
        return publish(adapter.reduce(WorkspaceAction.CancelSelectionSession).renderState)
    }

    fun onApply(): EditorRenderState {
        adapter.selectionNotice = runtime.selectionOperations.applySelection().selectionNotice()
        return publish(adapter.renderState)
    }

    fun beginPointer(position: PixelPosition): PointerInputAcknowledgement =
        adapter.reduce(WorkspaceAction.BeginSelectionPointer(position))

    /** Pointer-up sends the final signed point before finishing. It never executes a document command. */
    fun finishPointer(point: DocumentPoint): PointerInputAcknowledgement {
        val updated = adapter.reduce(WorkspaceAction.UpdateSelectionPointer(point))
        return if (updated is PointerInputAcknowledgement.Rejected) {
            adapter.reduce(WorkspaceAction.CancelSelectionPointer)
        } else {
            adapter.reduce(WorkspaceAction.FinishSelectionPointer)
        }
    }
}

private fun SelectionApplyResult.selectionNotice(): SelectionEditorNotice? =
    when (this) {
        SelectionApplyResult.Applied, SelectionApplyResult.NoChange -> null
        is SelectionApplyResult.Rejected -> SelectionEditorNotice.Rejected(rejection)
        is SelectionApplyResult.CommandRejected -> SelectionEditorNotice.CommandRejected(reason)
        is SelectionApplyResult.CommandFailed -> SelectionEditorNotice.CommandFailed(failure)
    }
