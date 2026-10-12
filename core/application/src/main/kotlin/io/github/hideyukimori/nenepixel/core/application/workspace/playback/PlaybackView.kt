package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Visible projection only; private sampled time and fractional progress do not affect render equality. */
public data class PlaybackView internal constructor(
    public val mode: PlaybackMode,
    public val token: PlaybackToken?,
    public val frameId: FrameId?,
)
