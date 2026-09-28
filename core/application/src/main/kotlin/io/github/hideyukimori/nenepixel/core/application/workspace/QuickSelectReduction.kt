package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
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
                    state.withEditTarget(state.editTarget.withPaletteIndex(item.index)).withQuickSelection(closed),
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

/**
 * Reads the slot index under [WorkspaceAction.PickPaletteEntryAt.position] on the active layer, never its colour
 * (ADR 0030); an empty cell or a hidden active layer is rejected without changing the selection.
 */
private fun pickPaletteEntryAt(
    state: WorkspaceState,
    action: WorkspaceAction.PickPaletteEntryAt,
    source: CommandSourceAdmission,
): WorkspaceReductionResult {
    val layer = source.document.layers.firstOrNull { it.id == state.activeLayerId }
    return when {
        state.quickSelection.eyedropper == EyedropperState.Idle -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.EyedropperNotArmed)
        }

        layer == null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.ActiveLayerNotFound(state.activeLayerId))
        }

        layer.visibility == LayerVisibility.Hidden -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.ActiveLayerHidden(layer.id))
        }

        else -> {
            pickFromLayer(state, layer, action, source)
        }
    }
}

private fun pickFromLayer(
    state: WorkspaceState,
    layer: Layer,
    action: WorkspaceAction.PickPaletteEntryAt,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when (val read = layer.snapshot.cellAt(action.position)) {
        is DomainValueResult.Rejected -> {
            WorkspaceReductionResult.Rejected(
                state,
                WorkspaceActionRejection.PickPositionOutsideCanvas(source.document.size, action.position),
            )
        }

        is DomainValueResult.Created -> {
            pickCell(state, read.value, action, source.document.definition.palette)
        }
    }

private fun pickCell(
    state: WorkspaceState,
    cell: PixelCell,
    action: WorkspaceAction.PickPaletteEntryAt,
    palette: Palette,
): WorkspaceReductionResult =
    when (cell) {
        PixelCell.Empty -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.PickEmptyCell(action.position))
        }

        is PixelCell.Covered -> {
            selectingPaletteSlot(state, cell.index, palette) {
                WorkspaceReductionResult.Reduced(
                    state
                        .withEditTarget(state.editTarget.withPaletteIndex(cell.index))
                        .withQuickSelection(state.quickSelection.idle()),
                )
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
