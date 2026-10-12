package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** One atomic focus reduction; palette drafts and picked import targets remain document-bound. */
internal fun selectFrame(
    state: WorkspaceState,
    frameId: FrameId,
    document: DocumentState,
): WorkspaceReductionResult {
    val frame = document.frameById(frameId)
    return when {
        state.paletteEditSession?.isColorEditActive == true -> {
            WorkspaceReductionResult.Rejected(
                state,
                WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.ColorEditActive),
            )
        }

        frame == null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.FrameNotFound(frameId))
        }

        frameId == state.activeFrameId -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ActiveFrameAlreadySelected)
        }

        else -> {
            val settled =
                state
                    .withPreview(null)
                    .withSelectionSession(null)
                    .withQuickSelection(state.quickSelection.closed().idle())
            val rested =
                settled.underlay?.let { underlay ->
                    reduceReferenceUnderlay(settled, WorkspaceAction.SetReferenceUnderlay(underlay.rested())).nextState
                } ?: settled
            WorkspaceReductionResult.Reduced(
                rested.withEditTarget(rested.editTarget.withFrameLayer(frame.id, frame.layers.last().id)),
            )
        }
    }
}
