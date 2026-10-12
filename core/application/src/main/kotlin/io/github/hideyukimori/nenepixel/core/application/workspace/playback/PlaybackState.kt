package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** The sole canonical immutable playback value; session timing is deliberately hidden from the view. */
public class PlaybackState private constructor(
    internal val session: PlaybackSession?,
    public val view: PlaybackView,
) {
    internal val isPlaying: Boolean get() = session != null

    internal fun stop(): PlaybackState =
        if (session ==
            null
        ) {
            this
        } else {
            PlaybackState(null, PlaybackView(view.mode, null, null))
        }

    internal fun withMode(mode: PlaybackMode): PlaybackState =
        if (view.mode == mode) this else PlaybackState(null, PlaybackView(mode, null, null))

    internal fun start(
        plan: PlaybackTimingPlan,
        frameId: FrameId,
    ): PlaybackState = withSession(PlaybackSession.begin(plan, frameId))

    internal fun withSession(session: PlaybackSession): PlaybackState {
        val nextView =
            if (view.token === session.token &&
                view.frameId == session.frameId
            ) {
                view
            } else {
                PlaybackView(view.mode, session.token, session.frameId)
            }
        return if (this.session === session) this else PlaybackState(session, nextView)
    }

    public companion object {
        public val initial: PlaybackState = PlaybackState(null, PlaybackView(PlaybackMode.Loop, null, null))
    }
}
