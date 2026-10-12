package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

internal class PlaybackSession private constructor(
    val token: PlaybackToken,
    private val plan: PlaybackTimingPlan,
    private val position: Long,
    private val previousNanos: Long?,
) {
    private val index: Int = plan.indexAt(position)
    val frameId: FrameId get() = plan.frame(index)
    val remainingNanos: Long get() = plan.endpoint(index) - position

    fun sample(
        mode: PlaybackMode,
        nanos: Long,
    ): PlaybackSessionStep {
        val previous = previousNanos
        val delta = if (previous == null) 0L else nanos - previous
        return when {
            delta < 0L -> {
                PlaybackSessionStep.ClockDiscontinuity
            }

            mode == PlaybackMode.Once && delta >= plan.totalNanos - position -> {
                PlaybackSessionStep.Completed
            }

            else -> {
                val next =
                    if (mode ==
                        PlaybackMode.Loop
                    ) {
                        (position + delta % plan.totalNanos) % plan.totalNanos
                    } else {
                        position + delta
                    }
                val session = if (nanos == previousNanos) this else PlaybackSession(token, plan, next, nanos)
                PlaybackSessionStep.Advanced(session)
            }
        }
    }

    companion object {
        fun begin(
            plan: PlaybackTimingPlan,
            frameId: FrameId,
        ): PlaybackSession = PlaybackSession(PlaybackToken(), plan, plan.prefix(frameId), null)
    }
}

internal sealed interface PlaybackSessionStep {
    data class Advanced(
        val session: PlaybackSession,
    ) : PlaybackSessionStep

    data object Completed : PlaybackSessionStep

    data object ClockDiscontinuity : PlaybackSessionStep
}
