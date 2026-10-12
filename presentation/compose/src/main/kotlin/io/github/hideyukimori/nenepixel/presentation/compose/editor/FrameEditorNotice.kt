package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Expected frame outcomes retain their original owner vocabulary until localized here. */
internal sealed interface FrameEditorNotice {
    data class Rejected(
        val rejection: WorkspaceActionRejection,
    ) : FrameEditorNotice

    data class CommandRejected(
        val reason: RejectionReason,
    ) : FrameEditorNotice

    data class Failed(
        val failure: CommandFailure,
    ) : FrameEditorNotice
}

internal fun FrameEditorNotice.resource(): Int =
    when (this) {
        is FrameEditorNotice.Rejected -> {
            rejection.frameResource()
        }

        is FrameEditorNotice.CommandRejected -> {
            reason.frameResource()
        }

        is FrameEditorNotice.Failed -> {
            when (failure) {
                CommandFailure.PersistenceBusy -> R.string.frame_busy
                CommandFailure.PaletteSessionActive -> R.string.palette_notice_session_active
            }
        }
    }

private fun WorkspaceActionRejection.frameResource(): Int =
    when (this) {
        WorkspaceActionRejection.PersistenceBusy -> {
            R.string.frame_busy
        }

        WorkspaceActionRejection.PaletteSessionActive -> {
            R.string.palette_notice_session_active
        }

        is WorkspaceActionRejection.FrameNotFound -> {
            R.string.frame_missing
        }

        is WorkspaceActionRejection.PaletteDraftRejected -> {
            if (reason == PaletteDraftRejection.ColorEditActive) {
                R.string.palette_notice_color_edit_active
            } else {
                R.string.frame_unavailable
            }
        }

        is WorkspaceActionRejection.PlaybackRejected -> {
            reason.playbackResource()
        }

        else -> {
            R.string.frame_unavailable
        }
    }

private fun RejectionReason.frameResource(): Int =
    when {
        this is RejectionReason.FrameNotFound -> R.string.frame_missing
        this == RejectionReason.LastFrameNotDeletable -> R.string.frame_last
        this == RejectionReason.FrameLimitReached -> R.string.frame_limit
        this == RejectionReason.LayerLimitReached -> R.string.document_layer_limit
        else -> R.string.frame_unavailable
    }

private fun PlaybackRejection.playbackResource(): Int =
    when (this) {
        PlaybackRejection.InsufficientFrames -> R.string.playback_requires_frames
        PlaybackRejection.PendingImport -> R.string.playback_import_pending
        PlaybackRejection.Playing -> R.string.playback_stop_to_edit
        PlaybackRejection.ClockDiscontinuity -> R.string.playback_clock_stopped
    }
