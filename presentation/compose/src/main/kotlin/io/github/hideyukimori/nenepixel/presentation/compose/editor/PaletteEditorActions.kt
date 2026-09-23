package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits

/** The palette editor view of the draft: its slots, the selected slot (clamped into the draft) and editability. */
internal class PaletteEditorSelection private constructor(
    val entries: List<PaletteEntry>,
    val selected: Int,
    val defaultIndex: PaletteIndex,
    val editable: Boolean,
) {
    val selectedEntry: PaletteEntry
        get() = entries[selected]

    val selectedIsDefault: Boolean
        get() = selectedEntry.index == defaultIndex

    val canAppend: Boolean
        get() = editable && entries.size < PaletteLimits.MAX_ENTRY_COUNT

    val canRemove: Boolean
        get() = editable && entries.size > PaletteLimits.MIN_DEFINITION_ENTRY_COUNT

    fun canMove(offset: Int): Boolean = editable && (selected + offset) in entries.indices

    companion object {
        fun of(
            session: PaletteEditSession,
            selected: Int,
        ): PaletteEditorSelection {
            val entries = session.draft.palette.entries()
            return PaletteEditorSelection(
                entries,
                selected.coerceIn(0, entries.lastIndex),
                session.draft.defaultIndex,
                session.pendingImport == null,
            )
        }
    }
}

/** Panel-local choices that survive recomposition: the selected slot and whether removal waits for a replacement. */
internal class PaletteEditorChoice(
    selectedState: MutableIntState,
    choosingState: MutableState<Boolean>,
) {
    var selected: Int by selectedState
    var choosingReplacement: Boolean by choosingState
}

/** Turns editor gestures into `PaletteDraftOperation`s; the selection follows a slot only when the edit succeeded. */
internal class PaletteEditorActions(
    private val selection: PaletteEditorSelection,
    private val choice: PaletteEditorChoice,
    private val callbacks: EditorPaletteCallbacks,
) {
    fun slotTapped(position: Int) {
        if (!choice.choosingReplacement) {
            choice.selected = position
            return
        }
        choice.choosingReplacement = false
        if (position != selection.selected) {
            val replacement = selection.entries[position].index
            val removed = PaletteDraftOperation.RemoveSlot(selection.selectedEntry.index, replacement)
            val shifted = if (position > selection.selected) position - 1 else position
            edit(removed) { choice.selected = shifted }
        }
    }

    fun setColor(color: PixelColor) {
        edit(PaletteDraftOperation.SetSlotColor(selection.selectedEntry.index, color)) {}
    }

    fun setDefault() {
        edit(PaletteDraftOperation.SetDefault(selection.selectedEntry.index)) {}
    }

    fun append() {
        val appended = selection.entries.size
        edit(PaletteDraftOperation.AppendSlot(selection.selectedEntry.color)) { choice.selected = appended }
    }

    fun remove() {
        if (selection.selectedIsDefault) {
            choice.choosingReplacement = !choice.choosingReplacement
        } else {
            edit(PaletteDraftOperation.RemoveSlot(selection.selectedEntry.index)) {}
        }
    }

    fun move(offset: Int) {
        val target = selection.selected + offset
        val order = selection.entries.map { it.index }.toMutableList()
        order[selection.selected] = selection.entries[target].index
        order[target] = selection.selectedEntry.index
        edit(PaletteDraftOperation.Reorder(order)) { choice.selected = target }
    }

    private fun edit(
        operation: PaletteDraftOperation,
        succeeded: () -> Unit,
    ) {
        if (callbacks.onEdit(operation).paletteNotice == null) succeeded()
    }
}
