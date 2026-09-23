package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteApplyResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteImportMode
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/**
 * The palette-draft route (ADR 0022), reached through [EditorCallbacks.palette]. Every call goes through the runtime's
 * palette operations or `WorkspaceAction`s, records the refusal (or its absence) as the render state's
 * `paletteNotice`, and publishes the resulting render state.
 */
internal class EditorPaletteCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onBegin(): EditorRenderState = adapter.settle(runtime.paletteOperations.beginPaletteEdit().notice(), publish)

    fun onCancel(): EditorRenderState = reduce(WorkspaceAction.CancelPaletteEdit)

    fun onEdit(operation: PaletteDraftOperation): EditorRenderState =
        reduce(WorkspaceAction.EditPaletteDraft(operation))

    fun onUndo(): EditorRenderState = reduce(WorkspaceAction.UndoPaletteDraft)

    fun onRedo(): EditorRenderState = reduce(WorkspaceAction.RedoPaletteDraft)

    fun onApply(): EditorRenderState = adapter.settle(runtime.paletteOperations.applyPaletteDraft().notice(), publish)

    fun onImportMode(mode: PaletteImportMode): EditorRenderState = reduce(WorkspaceAction.SetPaletteImportMode(mode))

    fun onAssignImport(
        source: PaletteIndex,
        destination: PaletteIndex,
    ): EditorRenderState = reduce(WorkspaceAction.AssignPaletteImportSlot(source, destination))

    fun onConfirmImport(): EditorRenderState = reduce(WorkspaceAction.ConfirmPaletteImport)

    fun onCancelImport(): EditorRenderState = reduce(WorkspaceAction.CancelPaletteImport)

    private fun reduce(action: WorkspaceAction.PaletteSessionAction): EditorRenderState =
        adapter.settle(runtime.reduce(action).notice(), publish)
}

private fun EditorRuntimeAdapter.settle(
    notice: PaletteEditorNotice?,
    publish: (EditorRenderState) -> EditorRenderState,
): EditorRenderState {
    paletteNotice = notice
    return publish(renderState)
}

private fun WorkspaceReductionResult.notice(): PaletteEditorNotice? =
    (this as? WorkspaceReductionResult.Rejected)?.let { PaletteEditorNotice.Rejected(it.rejection) }

private fun PaletteApplyResult.notice(): PaletteEditorNotice? =
    when (this) {
        PaletteApplyResult.Applied,
        PaletteApplyResult.NoChange,
        -> null

        is PaletteApplyResult.Rejected -> PaletteEditorNotice.Rejected(rejection)

        is PaletteApplyResult.CommandRejected -> PaletteEditorNotice.ApplyRejected(reason)

        is PaletteApplyResult.CommandFailed -> PaletteEditorNotice.ApplyFailed(failure)
    }
