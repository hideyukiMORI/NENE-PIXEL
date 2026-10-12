package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * Snapshot-free row metadata, frame-local order and document-wide logical capacity.
 * Local order gates moves and the last-layer deletion; global count gates duplication.
 */
internal data class LayerRowEntry(
    val row: LayerRowModel,
    val current: Boolean,
    val order: LayerRowOrder,
    val totalLayerCount: Int,
) {
    val position: Int get() = order.position
    val layerCount: Int get() = order.count
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

    /** The front-most row: "Move up" is disabled there. */
    val frontMost: Boolean
        get() = position == layerCount - 1

    /** The back-most row: "Move down" is disabled there. */
    val backMost: Boolean
        get() = position == 0

    /** The frame's only layer: "Delete", "Move up" and "Move down" are all disabled. */
    val only: Boolean
        get() = layerCount == 1

    /** Any row can be duplicated while the shared bounded layer list has capacity. */
    val canDuplicate: Boolean
        get() = totalLayerCount < FrameLimits.MAX_TOTAL_LAYER_RECORDS

    /** The position "Move up" asks for: one step towards the front. */
    val moveUpPosition: Int
        get() = position + 1

    /** The position "Move down" asks for: one step towards the back. */
    val moveDownPosition: Int
        get() = position - 1
}
