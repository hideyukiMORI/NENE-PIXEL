package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Typed selection Apply refusals, localized only at the presentation boundary. */
internal sealed interface SelectionEditorNotice {
    data class Rejected(
        val rejection: WorkspaceActionRejection,
    ) : SelectionEditorNotice

    data class CommandRejected(
        val reason: RejectionReason,
    ) : SelectionEditorNotice

    data class CommandFailed(
        val failure: CommandFailure,
    ) : SelectionEditorNotice
}

internal fun SelectionEditorNotice.resource(): Int =
    when (this) {
        is SelectionEditorNotice.Rejected -> {
            if (rejection == WorkspaceActionRejection.PersistenceBusy) {
                R.string.selection_busy
            } else {
                R.string.selection_rejected
            }
        }

        is SelectionEditorNotice.CommandRejected -> {
            R.string.selection_rejected
        }

        is SelectionEditorNotice.CommandFailed -> {
            if (failure == CommandFailure.PersistenceBusy) R.string.selection_busy else R.string.selection_rejected
        }
    }
