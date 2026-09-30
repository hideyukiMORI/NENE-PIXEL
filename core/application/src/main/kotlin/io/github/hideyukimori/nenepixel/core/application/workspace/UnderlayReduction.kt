package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction

/** Reduces the reference-underlay actions (ADR 0032); they never reject and no document command is emitted. */
internal fun reduceReferenceUnderlay(
    state: WorkspaceState,
    action: WorkspaceAction.ReferenceUnderlayAction,
): WorkspaceReductionResult =
    when (action) {
        is WorkspaceAction.SetReferenceUnderlay -> setReferenceUnderlay(state, action.underlay)
        WorkspaceAction.ClearReferenceUnderlay -> clearReferenceUnderlay(state)
    }

/**
 * The underlay is non-modal: replacing it keeps every other field, the gesture preview included (ADR 0032).
 * This is the only caller of the internal [WorkspaceState] constructor besides `WorkspaceState.create`.
 */
internal fun WorkspaceState.withUnderlay(underlay: ReferenceUnderlay?): WorkspaceState =
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
    )

private fun setReferenceUnderlay(
    state: WorkspaceState,
    underlay: ReferenceUnderlay,
): WorkspaceReductionResult =
    when {
        state.underlay == underlay -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ReferenceUnderlayAlreadySet)
        }

        entersAdjusting(state.underlay, underlay) -> {
            WorkspaceReductionResult.Reduced(state.withPreview(null).withUnderlay(underlay))
        }

        else -> {
            WorkspaceReductionResult.Reduced(state.withUnderlay(underlay))
        }
    }

/** Entering the adjust mode cancels an in-progress gesture preview; staying in it or leaving it does not. */
private fun entersAdjusting(
    current: ReferenceUnderlay?,
    next: ReferenceUnderlay,
): Boolean =
    next.interaction == UnderlayInteraction.Adjusting &&
        (current == null || current.interaction == UnderlayInteraction.Resting)

private fun clearReferenceUnderlay(state: WorkspaceState): WorkspaceReductionResult =
    if (state.underlay == null) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.NoReferenceUnderlay)
    } else {
        WorkspaceReductionResult.Reduced(state.withUnderlay(null))
    }
