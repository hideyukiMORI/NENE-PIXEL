package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.SamplePlayback
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceNoChangeReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackPulseResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackStopReason
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackToken

/** Sole sample reduction; clock-fault stop is a second canonical action inside the same held lock. */
internal fun advancePlaybackLocked(
    transaction: EditorRuntime.RuntimeTransaction,
    token: PlaybackToken,
    nanos: Long,
): PlaybackPulseResult =
    when (val result = transaction.reduceWorkspace(SamplePlayback(token, nanos))) {
        is WorkspaceReductionResult.Rejected -> {
            check(result.rejection == WorkspaceActionRejection.PlaybackRejected(PlaybackRejection.ClockDiscontinuity))
            transaction.reduceWorkspace(WorkspaceAction.StopPlaybackIfCurrent(token))
            PlaybackPulseResult.Stopped(PlaybackStopReason.ClockDiscontinuity)
        }

        is WorkspaceReductionResult.Unchanged -> {
            if (result.reason ==
                WorkspaceNoChangeReason.PlaybackTokenNotCurrent
            ) {
                PlaybackPulseResult.Stale
            } else {
                currentPulse(transaction)
            }
        }

        is WorkspaceReductionResult.Reduced -> {
            currentPulse(transaction)
        }

        is WorkspaceReductionResult.CommitPrepared -> {
            error("Playback sampling cannot prepare a document command")
        }
    }

private fun currentPulse(transaction: EditorRuntime.RuntimeTransaction): PlaybackPulseResult {
    val session = transaction.workspaceState().playback.session
    return if (session == null) {
        PlaybackPulseResult.Stopped(PlaybackStopReason.Completed)
    } else {
        PlaybackPulseResult.Continue(session.token, session.remainingNanos)
    }
}
