package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * What one layer-panel row shows (#144 UI spec "行"): the [row] and whether it is the active layer, plus the plain
 * mappings from that to the visibility toggle and the spoken state. [row] stays the only layer value a row reads.
 */
internal data class LayerRowEntry(
    val row: LayerRowModel,
    val current: Boolean,
) {
    val hidden: Boolean
        get() = row.visibility == LayerVisibility.Hidden

    /** The visibility a tap on the toggle asks for: always the opposite of the row's. */
    val toggleTarget: LayerVisibility
        get() = if (hidden) LayerVisibility.Visible else LayerVisibility.Hidden

    /** The toggle says what a tap does (`layer_show` / `layer_hide`), not the current state. */
    val toggleDescription: Int
        get() = if (hidden) R.string.layer_show else R.string.layer_hide

    /** The toggle's mark shows the current state: an open eye, or a closed eye while hidden. */
    val visibilityIcon: EditorIcon
        get() = if (hidden) EditorIcon.Hidden else EditorIcon.Visible

    /** `layer_row_state_current` for the active layer's row; other rows speak no state. */
    val stateDescription: Int?
        get() = if (current) R.string.layer_row_state_current else null
}
