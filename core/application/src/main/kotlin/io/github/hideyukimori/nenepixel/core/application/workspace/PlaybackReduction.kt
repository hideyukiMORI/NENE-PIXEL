package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackSessionStep
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTimingPlan
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction

internal fun reducePreviewSession(
    state: WorkspaceState,
    action: WorkspaceAction.PreviewSessionAction,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when (action) {
        is WorkspaceAction.SelectionAction -> reduceSelection(state, action)
        is WorkspaceAction.PlaybackAction -> reducePlayback(state, action, source)
    }

private fun reducePlayback(
    state: WorkspaceState,
    action: WorkspaceAction.PlaybackAction,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when (action) {
        WorkspaceAction.StartPlayback -> {
            startPlayback(state, source)
        }

        WorkspaceAction.StopPlayback -> {
            stopPlayback(state)
        }

        is WorkspaceAction.StopPlaybackIfCurrent -> {
            if (state.playback.view.token ===
                action.token
            ) {
                stopPlayback(state)
            } else {
                unchangedPlayback(state, WorkspaceNoChangeReason.PlaybackTokenNotCurrent)
            }
        }

        is WorkspaceAction.SetPlaybackMode -> {
            if (state.playback.view.mode ==
                action.mode
            ) {
                unchangedPlayback(state, WorkspaceNoChangeReason.PlaybackModeAlreadySet)
            } else {
                WorkspaceReductionResult.Reduced(state.withPlayback(state.playback.withMode(action.mode)))
            }
        }

        is SamplePlayback -> {
            samplePlayback(state, action)
        }
    }

private fun startPlayback(
    state: WorkspaceState,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when {
        state.paletteEditSession != null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.PaletteSessionActive)
        }

        state.pendingImport != null -> {
            rejectedPlayback(state, PlaybackRejection.PendingImport)
        }

        source.document.frameById(state.activeFrameId) == null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.FrameNotFound(state.activeFrameId))
        }

        source.document.frames.size < 2 -> {
            rejectedPlayback(state, PlaybackRejection.InsufficientFrames)
        }

        state.playback.isPlaying -> {
            unchangedPlayback(state, WorkspaceNoChangeReason.PlaybackAlreadyRunning)
        }

        else -> {
            val settled = settlePlaybackEntry(state, source)
            WorkspaceReductionResult.Reduced(
                settled.withPlayback(
                    settled.playback.start(PlaybackTimingPlan.create(source.document), settled.activeFrameId),
                ),
            )
        }
    }

private fun settlePlaybackEntry(
    state: WorkspaceState,
    source: CommandSourceAdmission,
): WorkspaceState {
    val stroke = reduceGesturePreview(state, WorkspaceAction.CancelGesturePreview, source).nextState
    val selection = reduceSelection(stroke, WorkspaceAction.CancelSelectionSession).nextState
    val menu = reduceQuickSelect(selection, WorkspaceAction.CancelQuickSelect, source).nextState
    val idle = reduceQuickSelect(menu, WorkspaceAction.DisarmEyedropper, source).nextState
    val underlay = idle.underlay
    return if (underlay?.interaction == UnderlayInteraction.Adjusting) {
        reduceReferenceUnderlay(idle, WorkspaceAction.SetReferenceUnderlay(underlay.rested())).nextState
    } else {
        idle
    }
}

private fun stopPlayback(state: WorkspaceState): WorkspaceReductionResult =
    if (state.playback.isPlaying) {
        WorkspaceReductionResult.Reduced(state.withPlayback(state.playback.stop()))
    } else {
        unchangedPlayback(state, WorkspaceNoChangeReason.PlaybackAlreadyStopped)
    }

private fun samplePlayback(
    state: WorkspaceState,
    action: SamplePlayback,
): WorkspaceReductionResult {
    val session = state.playback.session
    return if (session == null || session.token !== action.token) {
        unchangedPlayback(state, WorkspaceNoChangeReason.PlaybackTokenNotCurrent)
    } else {
        when (val step = session.sample(state.playback.view.mode, action.nanos)) {
            is PlaybackSessionStep.Advanced -> {
                if (step.session === session) {
                    unchangedPlayback(state, WorkspaceNoChangeReason.PlaybackSampleUnchanged)
                } else {
                    WorkspaceReductionResult.Reduced(state.withPlayback(state.playback.withSession(step.session)))
                }
            }

            PlaybackSessionStep.Completed -> {
                stopPlayback(state)
            }

            PlaybackSessionStep.ClockDiscontinuity -> {
                rejectedPlayback(state, PlaybackRejection.ClockDiscontinuity)
            }
        }
    }
}

private fun unchangedPlayback(
    state: WorkspaceState,
    reason: WorkspaceNoChangeReason,
): WorkspaceReductionResult = WorkspaceReductionResult.Unchanged(state, reason)

private fun rejectedPlayback(
    state: WorkspaceState,
    reason: PlaybackRejection,
): WorkspaceReductionResult =
    WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.PlaybackRejected(reason))
