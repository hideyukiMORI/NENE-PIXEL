package io.github.hideyukimori.nenepixel.core.application.workspace.playback

public sealed interface PlaybackRejection {
    public data object InsufficientFrames : PlaybackRejection

    public data object PendingImport : PlaybackRejection

    public data object Playing : PlaybackRejection

    public data object ClockDiscontinuity : PlaybackRejection
}
