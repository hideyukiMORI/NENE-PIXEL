package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction

internal fun WorkspaceAction.isPlaybackCleanup(): Boolean =
    this == WorkspaceAction.StopPlayback || this is WorkspaceAction.StopPlaybackIfCurrent

/** One policy is shared by direct and RuntimeTransaction reductions. Cleanup never ends a new session. */
internal fun WorkspaceAction.isPointerBlockedByPlayback(): Boolean =
    when (this) {
        is WorkspaceAction.BeginGesturePreview, is WorkspaceAction.ExtendGesturePreview,
        WorkspaceAction.PrepareGestureCommit, is WorkspaceAction.SetViewport,
        is WorkspaceAction.BeginSelectionPointer, is WorkspaceAction.UpdateSelectionPointer,
        WorkspaceAction.FinishSelectionPointer,
        -> true

        WorkspaceAction.CancelGesturePreview, WorkspaceAction.CancelSelectionSession,
        WorkspaceAction.CancelSelectionPointer, is WorkspaceAction.SetSelectionMode,
        is WorkspaceAction.TransformSelection, is BeginSelectionSession,
        is WorkspaceAction.SetAppearance, is WorkspaceAction.SetActualSizeWindow,
        is WorkspaceAction.SelectPaletteEntry, is WorkspaceAction.SelectTool,
        is WorkspaceAction.PaletteSessionAction, is WorkspaceAction.QuickSelectAction,
        is DocumentReconciliation, is WorkspaceAction.EditTargetAction,
        is WorkspaceAction.ReferenceUnderlayAction, is WorkspaceAction.RasterImportAction,
        is WorkspaceAction.PlaybackAction,
        -> false
    }

internal fun WorkspaceReductionResult.stopPlaybackForIncompatibleAction(
    action: WorkspaceAction,
): WorkspaceReductionResult =
    if (this is WorkspaceReductionResult.Reduced && nextState.playback.isPlaying && action.endsPlayback(nextState)) {
        WorkspaceReductionResult.Reduced(nextState.withPlayback(nextState.playback.stop()))
    } else {
        this
    }

private fun WorkspaceAction.endsPlayback(next: WorkspaceState): Boolean =
    when (this) {
        is BeginPaletteEdit, is BeginSelectionSession, is WorkspaceAction.SetPendingRasterImport,
        is WorkspaceAction.EditTargetAction, is WorkspaceAction.SelectTool,
        -> {
            true
        }

        is WorkspaceAction.QuickSelectAction -> {
            next.quickSelection.menu != null ||
                next.quickSelection.eyedropper == EyedropperState.Armed
        }

        is WorkspaceAction.SetReferenceUnderlay -> {
            next.underlay?.interaction == UnderlayInteraction.Adjusting
        }

        is WorkspaceAction.SetAppearance, is WorkspaceAction.SetActualSizeWindow,
        is WorkspaceAction.SelectPaletteEntry, is WorkspaceAction.GesturePreviewAction,
        is WorkspaceAction.SetViewport, is WorkspaceAction.PaletteSessionAction,
        is DocumentReconciliation, WorkspaceAction.ClearReferenceUnderlay,
        WorkspaceAction.ClearPendingRasterImport, is WorkspaceAction.SelectionAction,
        is WorkspaceAction.PlaybackAction,
        -> {
            false
        }
    }
