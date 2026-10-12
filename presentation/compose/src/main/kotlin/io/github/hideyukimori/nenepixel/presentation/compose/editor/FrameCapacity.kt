package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameLimits

/** Logical capacity is shared across frames even when their immutable snapshots share a reference. */
internal data class FrameCapacity(
    val frameCount: Int,
    val layerCount: Int,
) {
    val remainingLayers: Int get() = FrameLimits.MAX_TOTAL_LAYER_RECORDS - layerCount
    val canAdd: Boolean get() = frameCount < FrameLimits.MAX_FRAMES && remainingLayers > 0

    fun canDuplicate(layerCount: Int): Boolean = canAdd && layerCount <= remainingLayers
}
