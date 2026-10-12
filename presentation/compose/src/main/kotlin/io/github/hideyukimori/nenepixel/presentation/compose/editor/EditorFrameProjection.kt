package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackView
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Visible frame facts only; editing focus and playback timing remain owned by the runtime. */
internal data class EditorFrameProjection(
    val editingFrameId: FrameId,
    val playback: PlaybackView,
) {
    val displayedFrameId: FrameId get() = playback.frameId ?: editingFrameId
}
