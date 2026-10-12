package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

internal data class FrameRowModel(
    val id: FrameId,
    val duration: FrameDuration,
    val layerCount: Int,
)
