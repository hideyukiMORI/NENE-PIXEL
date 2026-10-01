package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftTransition
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState

public class WorkspaceReducer private constructor() {
    public fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
        source: CommandSourceAdmission,
    ): WorkspaceReductionResult =
        when (action) {
            is WorkspaceAction.SetAppearance -> setAppearance(state, action.appearance)
            is WorkspaceAction.SetActualSizeWindow -> setActualSizeWindow(state, action.window)
            is WorkspaceAction.SelectPaletteEntry -> selectPaletteEntry(state, action, source)
            is WorkspaceAction.SelectTool -> selectTool(state, action)
            is WorkspaceAction.GesturePreviewAction -> reduceGesturePreview(state, action, source)
            is WorkspaceAction.SetViewport -> setViewport(state, action)
            is WorkspaceAction.PaletteSessionAction -> reducePaletteSession(state, action)
            is WorkspaceAction.QuickSelectAction -> reduceQuickSelect(state, action, source)
            is DocumentReconciliation -> reconcileDocument(state, action, source.document)
            is WorkspaceAction.LayerAction -> reduceLayer(state, action, source.document)
            is WorkspaceAction.ReferenceUnderlayAction -> reduceReferenceUnderlay(state, action)
        }

    private fun selectPaletteEntry(
        state: WorkspaceState,
        action: WorkspaceAction.SelectPaletteEntry,
        source: CommandSourceAdmission,
    ): WorkspaceReductionResult =
        selectingPaletteSlot(state, action.index, source.document.definition.palette) {
            if (action.index == state.activePaletteIndex) {
                unchanged(state, WorkspaceNoChangeReason.ActivePaletteEntryAlreadySelected)
            } else {
                WorkspaceReductionResult.Reduced(state.withEditTarget(state.editTarget.withPaletteIndex(action.index)))
            }
        }

    private fun setViewport(
        state: WorkspaceState,
        action: WorkspaceAction.SetViewport,
    ): WorkspaceReductionResult =
        if (action.viewport == state.viewport && state.preview == null) {
            unchanged(state, WorkspaceNoChangeReason.ViewportAlreadySet)
        } else {
            WorkspaceReductionResult.Reduced(state.withViewport(action.viewport))
        }

    private fun unchanged(
        state: WorkspaceState,
        reason: WorkspaceNoChangeReason,
    ): WorkspaceReductionResult = WorkspaceReductionResult.Unchanged(state, reason)

    public companion object {
        public fun create(): WorkspaceReducer = WorkspaceReducer()
    }
}

/** Selecting a tool also returns an armed eyedropper to idle, even for the current tool (ADR 0029). */
private fun selectTool(
    state: WorkspaceState,
    action: WorkspaceAction.SelectTool,
): WorkspaceReductionResult =
    if (action.tool == state.activeTool && state.quickSelection.eyedropper == EyedropperState.Idle) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ActiveToolAlreadySelected)
    } else {
        WorkspaceReductionResult.Reduced(
            state.withActiveTool(action.tool).withQuickSelection(state.quickSelection.idle()),
        )
    }

private fun setAppearance(
    state: WorkspaceState,
    appearance: EditorAppearance,
): WorkspaceReductionResult =
    if (state.appearance == appearance && state.preview == null) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.AppearanceAlreadySet)
    } else {
        WorkspaceReductionResult.Reduced(state.withAppearance(appearance))
    }

private fun setActualSizeWindow(
    state: WorkspaceState,
    window: ActualSizeWindow,
): WorkspaceReductionResult =
    if (state.actualSizeWindow == window) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ActualSizeWindowAlreadySet)
    } else {
        WorkspaceReductionResult.Reduced(state.withActualSizeWindow(window))
    }

private fun reducePaletteSession(
    state: WorkspaceState,
    action: WorkspaceAction.PaletteSessionAction,
): WorkspaceReductionResult =
    when (action) {
        is BeginPaletteEdit -> beginPaletteEdit(state, action)

        WorkspaceAction.CancelPaletteEdit -> cancelPaletteEdit(state)

        is WorkspaceAction.EditPaletteDraft -> editPaletteDraft(state, action)

        WorkspaceAction.UndoPaletteDraft -> transitionPaletteDraft(state, PaletteEditSession::undo)

        WorkspaceAction.RedoPaletteDraft -> transitionPaletteDraft(state, PaletteEditSession::redo)

        is WorkspaceAction.ImportPaletteDraft,
        is WorkspaceAction.SetPaletteImportMode,
        is WorkspaceAction.AssignPaletteImportSlot,
        WorkspaceAction.ConfirmPaletteImport,
        WorkspaceAction.CancelPaletteImport,
        -> reducePaletteImport(state, action)
    }

private fun beginPaletteEdit(
    state: WorkspaceState,
    action: BeginPaletteEdit,
): WorkspaceReductionResult =
    if (state.paletteEditSession != null) {
        WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.PaletteSessionAlreadyActive)
    } else {
        WorkspaceReductionResult.Reduced(
            state.withPaletteEditSession(PaletteEditSession.begin(action.base, action.definition)),
        )
    }

private fun cancelPaletteEdit(state: WorkspaceState): WorkspaceReductionResult =
    if (state.paletteEditSession == null) {
        WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.NoPaletteSession)
    } else {
        WorkspaceReductionResult.Reduced(state.withPaletteEditSession(null))
    }

private fun editPaletteDraft(
    state: WorkspaceState,
    action: WorkspaceAction.EditPaletteDraft,
): WorkspaceReductionResult = transitionPaletteDraft(state) { session -> session.edit(action.operation) }

/** The one mapping from a draft transition to a workspace reduction; only the palette session slot changes. */
internal fun transitionPaletteDraft(
    state: WorkspaceState,
    step: (PaletteEditSession) -> PaletteDraftTransition,
): WorkspaceReductionResult {
    val session =
        state.paletteEditSession
            ?: return WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.NoPaletteSession)
    return when (val transition = step(session)) {
        is PaletteDraftTransition.Changed -> {
            WorkspaceReductionResult.Reduced(state.withPaletteEditSession(transition.session))
        }

        PaletteDraftTransition.Unchanged -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.PaletteDraftUnchanged)
        }

        is PaletteDraftTransition.Rejected -> {
            WorkspaceReductionResult.Rejected(state, transition.rejection)
        }
    }
}
