package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteApplyResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation

/**
 * The palette-draft route (ADR 0022), reached through [EditorCallbacks.palette]. Every call goes through the runtime's
 * palette operations or `WorkspaceAction`s, records the refusal (or its absence) as the render state's
 * `lastPaletteRejection`, and publishes the resulting render state.
 */
internal class EditorPaletteCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onBegin(): EditorRenderState = settle(runtime.paletteOperations.beginPaletteEdit().rejection())

    fun onCancel(): EditorRenderState = reduce(WorkspaceAction.CancelPaletteEdit)

    fun onEdit(operation: PaletteDraftOperation): EditorRenderState =
        reduce(WorkspaceAction.EditPaletteDraft(operation))

    fun onUndo(): EditorRenderState = reduce(WorkspaceAction.UndoPaletteDraft)

    fun onRedo(): EditorRenderState = reduce(WorkspaceAction.RedoPaletteDraft)

    fun onApply(): EditorRenderState =
        settle(
            when (val result = runtime.paletteOperations.applyPaletteDraft()) {
                is PaletteApplyResult.Rejected -> result.rejection

                PaletteApplyResult.Applied,
                PaletteApplyResult.NoChange,
                is PaletteApplyResult.CommandRejected,
                is PaletteApplyResult.CommandFailed,
                -> null
            },
        )

    private fun reduce(action: WorkspaceAction.PaletteSessionAction): EditorRenderState =
        settle(runtime.reduce(action).rejection())

    private fun settle(rejection: WorkspaceActionRejection?): EditorRenderState {
        adapter.lastPaletteRejection = rejection
        return publish(adapter.renderState)
    }
}

private fun WorkspaceReductionResult.rejection(): WorkspaceActionRejection? =
    (this as? WorkspaceReductionResult.Rejected)?.rejection
