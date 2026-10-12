package io.github.hideyukimori.nenepixel.core.application.workspace.playback

public enum class PlaybackStopReason { Completed, ClockDiscontinuity }

public sealed interface PlaybackPulseResult {
    public data class Continue(
        public val token: PlaybackToken,
        public val remainingNanos: Long,
    ) : PlaybackPulseResult

    public data class Stopped(
        public val reason: PlaybackStopReason,
    ) : PlaybackPulseResult

    public data object Stale : PlaybackPulseResult
}
