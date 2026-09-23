package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.assignImportSlot
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.cancelImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.confirmImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.setImportMode
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.stageImport

/** Reduces the pending-import actions of an open palette session; only the palette session slot changes. */
internal fun reducePaletteImport(
    state: WorkspaceState,
    action: WorkspaceAction.PaletteSessionAction,
): WorkspaceReductionResult =
    when (action) {
        is WorkspaceAction.ImportPaletteDraft -> importPaletteDraft(state, action)
        is WorkspaceAction.SetPaletteImportMode -> setPaletteImportMode(state, action)
        is WorkspaceAction.AssignPaletteImportSlot -> assignPaletteImportSlot(state, action)
        WorkspaceAction.ConfirmPaletteImport -> transitionPaletteDraft(state) { session -> session.confirmImport() }
        WorkspaceAction.CancelPaletteImport -> transitionPaletteDraft(state) { session -> session.cancelImport() }
        else -> error("Not a palette import action: $action")
    }

private fun importPaletteDraft(
    state: WorkspaceState,
    action: WorkspaceAction.ImportPaletteDraft,
): WorkspaceReductionResult = transitionPaletteDraft(state) { session -> session.stageImport(action.definition) }

private fun setPaletteImportMode(
    state: WorkspaceState,
    action: WorkspaceAction.SetPaletteImportMode,
): WorkspaceReductionResult = transitionPaletteDraft(state) { session -> session.setImportMode(action.mode) }

private fun assignPaletteImportSlot(
    state: WorkspaceState,
    action: WorkspaceAction.AssignPaletteImportSlot,
): WorkspaceReductionResult =
    transitionPaletteDraft(state) { session -> session.assignImportSlot(action.source, action.destination) }
