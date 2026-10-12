package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Local panel navigation is separate from the canonical frame selection/commands. */
internal class FramePanelActions(
    val callbacks: EditorFrameCallbacks,
    val select: (FrameId) -> Unit,
    val back: () -> Unit,
) {
    val playback: EditorPlaybackCallbacks get() = callbacks.playback
}
