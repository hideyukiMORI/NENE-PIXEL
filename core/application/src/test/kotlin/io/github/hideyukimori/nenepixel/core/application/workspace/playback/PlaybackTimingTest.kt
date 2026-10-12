package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.workspace.SamplePlayback
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.MS
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.pulse
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.start
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PlaybackTimingTest {
    @Test fun `unequal loop intervals are half open and delayed cycles resolve directly`() {
        val runtime = runtime()
        val token = start(runtime)
        assertEquals(10 * MS, pulse(runtime, token, 500 * MS).remainingNanos)
        val first = runtime.state.workspaceState.playback.view
        assertEquals(1 * MS, pulse(runtime, token, 509 * MS).remainingNanos)
        assertSame(first, runtime.state.workspaceState.playback.view)
        assertEquals(30 * MS, pulse(runtime, token, 510 * MS).remainingNanos)
        assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
        assertEquals(20 * MS, pulse(runtime, token, 540 * MS).remainingNanos)
        assertEquals(id(8), runtime.state.workspaceState.playback.view.frameId)
        assertEquals(10 * MS, pulse(runtime, token, 560 * MS).remainingNanos)
        assertEquals(id(4), runtime.state.workspaceState.playback.view.frameId)
        pulse(runtime, token, 560 * MS + 1_000_000 * 60 * MS + 15 * MS)
        assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
        assertEquals(id(4), runtime.state.workspaceState.activeFrameId)
    }

    @Test fun `Once from middle or last preserves the full final logical interval then restores edit focus`() {
        listOf(2 to 50L, 8 to 20L).forEach { (frame, remaining) ->
            val runtime = runtime()
            runtime.reduce(WorkspaceAction.SelectFrame(id(frame)))
            val token = start(runtime, PlaybackMode.Once)
            pulse(runtime, token, 0L)
            pulse(runtime, token, (remaining - 1) * MS)
            assertEquals(id(8), runtime.state.workspaceState.playback.view.frameId)
            assertEquals(
                PlaybackPulseResult.Stopped(PlaybackStopReason.Completed),
                runtime.advancePlayback(
                    token,
                    remaining * MS,
                ),
            )
            assertNull(runtime.state.workspaceState.playback.view.frameId)
            assertEquals(id(frame), runtime.state.workspaceState.activeFrameId)
            assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(token, Long.MAX_VALUE))
        }
    }

    @Test fun `signed origins wrap equal samples and clock fault preserve atomic reducer refusal`() {
        val runtime = runtime()
        val token = start(runtime)
        val anchor = Long.MAX_VALUE - 5 * MS
        pulse(runtime, token, anchor)
        pulse(runtime, token, Long.MIN_VALUE + 5 * MS - 1L)
        assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
        val before = runtime.state.workspaceState
        pulse(runtime, token, Long.MIN_VALUE + 5 * MS - 1L)
        assertSame(before, runtime.state.workspaceState)
        val action = SamplePlayback(token, Long.MIN_VALUE + 4 * MS)
        val refused =
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                WorkspaceReducer.create().reduce(before, action, runtime.captureSource()),
            )
        assertEquals(WorkspaceActionRejection.PlaybackRejected(PlaybackRejection.ClockDiscontinuity), refused.rejection)
        assertSame(before, refused.nextState)
        assertEquals(
            PlaybackPulseResult.Stopped(PlaybackStopReason.ClockDiscontinuity),
            runtime.advancePlayback(token, action.nanos),
        )
        assertNull(runtime.state.workspaceState.playback.view.token)
        val negative = start(runtime)
        pulse(runtime, negative, -100 * MS)
        pulse(runtime, negative, -90 * MS)
        assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
    }

    @Test fun `LongMAX forward jump is bounded in Loop and finishes Once without overflow`() {
        val loop = runtime()
        val token = start(loop)
        pulse(loop, token, 0L)
        val remainder = Long.MAX_VALUE % (60 * MS)
        val next = pulse(loop, token, Long.MAX_VALUE)
        val expectedFrame =
            if (remainder < 10 * MS) {
                4
            } else if (remainder < 40 * MS) {
                2
            } else {
                8
            }
        val end =
            if (expectedFrame == 4) {
                10L
            } else if (expectedFrame == 2) {
                40L
            } else {
                60L
            }
        assertEquals(id(expectedFrame), loop.state.workspaceState.playback.view.frameId)
        assertEquals(end * MS - remainder, next.remainingNanos)
        val once = runtime()
        val onceToken = start(once, PlaybackMode.Once)
        pulse(once, onceToken, 0L)
        assertEquals(
            PlaybackPulseResult.Stopped(PlaybackStopReason.Completed),
            once.advancePlayback(onceToken, Long.MAX_VALUE),
        )
    }

    @Test fun `old pulse and disposal cannot stop a restart and identical controls are genuine no ops`() {
        val runtime = runtime()
        val old = start(runtime)
        val running = runtime.state.workspaceState
        assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, runtime.reduce(WorkspaceAction.StartPlayback))
        assertSame(running, runtime.state.workspaceState)
        runtime.reduce(WorkspaceAction.StopPlayback)
        val token = start(runtime)
        val restarted = runtime.state.workspaceState
        assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(old, 0L))
        runtime.reduce(WorkspaceAction.StopPlaybackIfCurrent(old))
        assertSame(restarted, runtime.state.workspaceState)
        runtime.reduce(WorkspaceAction.SetPlaybackMode(PlaybackMode.Loop))
        assertSame(restarted, runtime.state.workspaceState)
        runtime.reduce(WorkspaceAction.SetPlaybackMode(PlaybackMode.Once))
        assertNull(runtime.state.workspaceState.playback.view.token)
        assertEquals(PlaybackMode.Once, runtime.state.workspaceState.playback.view.mode)
        assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(token, 0L))
        val stopped = runtime.state.workspaceState
        runtime.reduce(WorkspaceAction.StopPlayback)
        assertSame(stopped, runtime.state.workspaceState)
    }
}
