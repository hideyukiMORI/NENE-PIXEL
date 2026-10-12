package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackToken

/** Explicit platform sample; only advancePlayback exposes the consuming pulse result. */
internal data class SamplePlayback(
    val token: PlaybackToken,
    val nanos: Long,
) : WorkspaceAction.PlaybackAction
