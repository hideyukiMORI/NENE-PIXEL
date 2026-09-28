package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Reduces the quick-select menu and eyedropper actions (ADR 0029); no document command is emitted. */
internal fun reduceQuickSelect(
    state: WorkspaceState,
    action: WorkspaceAction.QuickSelectAction,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when (action) {
        WorkspaceAction.OpenQuickSelect -> openQuickSelect(state)
        is WorkspaceAction.HighlightQuickSelectItem -> highlightQuickSelectItem(state, action.item)
        WorkspaceAction.ConfirmQuickSelect -> confirmQuickSelect(state, source.document.definition.palette)
        WorkspaceAction.CancelQuickSelect -> cancelQuickSelect(state)
        is WorkspaceAction.PickPaletteEntryAt -> pickPaletteEntryAt(state, action, source)
        WorkspaceAction.DisarmEyedropper -> disarmEyedropper(state)
    }

/**
 * The reducer's one slot selection (CMD-002): [index] outside [palette] is rejected without changing
 * [state]; otherwise [selected] builds the result.
 */
internal fun selectingPaletteSlot(
    state: WorkspaceState,
    index: PaletteIndex,
    palette: Palette,
    selected: () -> WorkspaceReductionResult,
): WorkspaceReductionResult =
    when (palette.entryAt(index)) {
        is DomainValueResult.Rejected -> {
            WorkspaceReductionResult.Rejected(
                state,
                WorkspaceActionRejection.PaletteIndexOutsidePalette(index, palette.entryCount),
            )
        }

        is DomainValueResult.Created -> {
            selected()
        }
    }

private fun openQuickSelect(state: WorkspaceState): WorkspaceReductionResult =
    when {
        state.preview != null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.PreviewAlreadyActive)
        }

        state.quickSelection.eyedropper == EyedropperState.Armed -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.EyedropperArmed)
        }

        state.quickSelection.menu != null -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.QuickSelectMenuAlreadyOpen)
        }

        else -> {
            WorkspaceReductionResult.Reduced(state.withQuickSelection(state.quickSelection.opened()))
        }
    }

private fun highlightQuickSelectItem(
    state: WorkspaceState,
    item: QuickSelectItem?,
): WorkspaceReductionResult {
    val menu = state.quickSelection.menu ?: return noQuickSelectMenu(state)
    return when {
        item != null && item !in menu.items -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.QuickSelectItemNotInMenu)
        }

        item == menu.highlighted -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.QuickSelectHighlightUnchanged)
        }

        else -> {
            WorkspaceReductionResult.Reduced(state.withQuickSelection(state.quickSelection.highlighting(item)))
        }
    }
}

/** Closes the menu and applies its highlight; a rejected slot leaves the menu open. */
private fun confirmQuickSelect(
    state: WorkspaceState,
    palette: Palette,
): WorkspaceReductionResult {
    val menu = state.quickSelection.menu ?: return noQuickSelectMenu(state)
    val closed = state.quickSelection.closed()
    return when (val item = menu.highlighted) {
        is QuickSelectItem.PaletteSlot -> {
            selectingPaletteSlot(state, item.index, palette) {
                WorkspaceReductionResult.Reduced(
                    state.withActivePaletteIndex(item.index).withQuickSelection(closed),
                )
            }
        }

        QuickSelectItem.Eyedropper -> {
            WorkspaceReductionResult.Reduced(state.withQuickSelection(closed.armed()))
        }

        null -> {
            WorkspaceReductionResult.Reduced(state.withQuickSelection(closed))
        }
    }
}

private fun cancelQuickSelect(state: WorkspaceState): WorkspaceReductionResult =
    if (state.quickSelection.menu == null) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.QuickSelectMenuAlreadyClosed)
    } else {
        WorkspaceReductionResult.Reduced(state.withQuickSelection(state.quickSelection.closed()))
    }

/** Reads the slot index under [WorkspaceAction.PickPaletteEntryAt.position], never its colour. */
private fun pickPaletteEntryAt(
    state: WorkspaceState,
    action: WorkspaceAction.PickPaletteEntryAt,
    source: CommandSourceAdmission,
): WorkspaceReductionResult {
    if (state.quickSelection.eyedropper == EyedropperState.Idle) {
        return WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.EyedropperNotArmed)
    }
    return when (val read = source.document.snapshot.indexAt(action.position)) {
        is DomainValueResult.Rejected -> {
            WorkspaceReductionResult.Rejected(
                state,
                WorkspaceActionRejection.PickPositionOutsideCanvas(source.document.size, action.position),
            )
        }

        is DomainValueResult.Created -> {
            selectingPaletteSlot(state, read.value, source.document.definition.palette) {
                WorkspaceReductionResult.Reduced(
                    state.withActivePaletteIndex(read.value).withQuickSelection(state.quickSelection.idle()),
                )
            }
        }
    }
}

private fun disarmEyedropper(state: WorkspaceState): WorkspaceReductionResult =
    if (state.quickSelection.eyedropper == EyedropperState.Idle) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.EyedropperAlreadyIdle)
    } else {
        WorkspaceReductionResult.Reduced(state.withQuickSelection(state.quickSelection.idle()))
    }

private fun noQuickSelectMenu(state: WorkspaceState): WorkspaceReductionResult =
    WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.NoQuickSelectMenu)
