package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** Row-local order and shared document capacity are immutable display projection. */
internal data class FrameRowEntry(
    val row: FrameRowModel,
    val current: Boolean,
    val position: Int,
    val capacity: FrameCapacity,
) {
    val canDelete: Boolean get() = capacity.frameCount > 1
    val canMoveEarlier: Boolean get() = position > 0
    val canMoveLater: Boolean get() = position < capacity.frameCount - 1
    val canDuplicate: Boolean get() = capacity.canDuplicate(row.layerCount)
}
