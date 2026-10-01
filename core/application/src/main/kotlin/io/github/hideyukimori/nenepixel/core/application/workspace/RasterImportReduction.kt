package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport

/** Reduces the pending PNG import actions (ADR 0033); they never reject and no document command is emitted. */
internal fun reduceRasterImport(
    state: WorkspaceState,
    action: WorkspaceAction.RasterImportAction,
): WorkspaceReductionResult =
    when (action) {
        is WorkspaceAction.SetPendingRasterImport -> setPendingRasterImport(state, action.pending)
        WorkspaceAction.ClearPendingRasterImport -> clearPendingRasterImport(state)
    }

/**
 * The pending import is undo-neutral and dirty-neutral: replacing it keeps every other field, the gesture preview
 * included (ADR 0033). Besides `WorkspaceState.create`, only this and `withUnderlay` call the internal
 * [WorkspaceState] constructor.
 */
internal fun WorkspaceState.withPendingImport(pending: PendingRasterImport?): WorkspaceState =
    WorkspaceState(
        editTarget,
        activeTool,
        viewport,
        preview,
        appearance,
        actualSizeWindow,
        paletteEditSession,
        quickSelection,
        underlay,
        pending,
    )

private fun setPendingRasterImport(
    state: WorkspaceState,
    pending: PendingRasterImport,
): WorkspaceReductionResult =
    if (state.pendingImport === pending) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.PendingRasterImportAlreadySet)
    } else {
        WorkspaceReductionResult.Reduced(state.withPendingImport(pending))
    }

private fun clearPendingRasterImport(state: WorkspaceState): WorkspaceReductionResult =
    if (state.pendingImport == null) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.NoPendingRasterImport)
    } else {
        WorkspaceReductionResult.Reduced(state.withPendingImport(null))
    }
