package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * Test identities, labels and the shown slot of the quick-select control and fan (ADR 0029).
 *
 * Slot numbers follow the existing palette UI: the one-based `index + 1` of `palette_entry`, `entry_number` and
 * `editor_palette_entry_<n>`, so one slot reads as the same number everywhere.
 */
internal object QuickSelectSemantics {
    const val CONTROL_TAG: String = "editor_quick_select"
    const val FAN_TAG: String = "editor_quick_select_fan"
    private const val SLOT_TAG_PREFIX: String = "editor_quick_select_slot_"
    private const val EYEDROPPER_TAG: String = "editor_quick_select_eyedropper"

    fun slotNumber(index: PaletteIndex): Int = index.value + 1

    fun itemTag(item: QuickSelectItem): String =
        when (item) {
            is QuickSelectItem.PaletteSlot -> SLOT_TAG_PREFIX + slotNumber(item.index)
            QuickSelectItem.Eyedropper -> EYEDROPPER_TAG
        }

    fun itemLabel(item: QuickSelectItem): QuickSelectLabel =
        when (item) {
            is QuickSelectItem.PaletteSlot -> {
                QuickSelectLabel(R.string.quick_select_slot, listOf(slotNumber(item.index)))
            }

            QuickSelectItem.Eyedropper -> {
                QuickSelectLabel(R.string.quick_select_eyedropper, emptyList())
            }
        }

    /** The control's state: the waiting eyedropper while armed, otherwise the active slot. */
    fun controlState(
        eyedropper: EyedropperState,
        active: PaletteIndex,
    ): QuickSelectLabel =
        when (eyedropper) {
            EyedropperState.Armed -> QuickSelectLabel(R.string.quick_select_state_eyedropper, emptyList())
            EyedropperState.Idle -> QuickSelectLabel(R.string.quick_select_state_slot, listOf(slotNumber(active)))
        }

    /** The slot the control shows: the highlighted slot during a drag preview, otherwise [active]. */
    fun shownSlot(
        selection: QuickSelection,
        active: PaletteIndex,
    ): PaletteIndex = (selection.menu?.highlighted as? QuickSelectItem.PaletteSlot)?.index ?: active

    /** The highlighted slot's number for the readout chip, or null when no slot is highlighted. */
    fun highlightedSlotNumber(selection: QuickSelection): Int? =
        (selection.menu?.highlighted as? QuickSelectItem.PaletteSlot)?.let { slotNumber(it.index) }
}

/** A localized string resource and its integer format arguments. */
internal data class QuickSelectLabel(
    val resource: Int,
    val arguments: List<Int>,
) {
    @Composable
    fun text(): String = stringResource(resource, *arguments.toTypedArray())
}
