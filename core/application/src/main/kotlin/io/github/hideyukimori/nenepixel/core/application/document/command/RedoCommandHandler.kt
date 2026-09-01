package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryEntry
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal class RedoCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: RedoCommand,
        historyEntry: HistoryEntry?,
    ): DocumentTransitionResult =
        when {
            command.targetDocumentId != currentState.id -> {
                rejected(RejectionReason.TargetDocumentMismatch(command.targetDocumentId, currentState.id))
            }

            command.targetRevision != currentState.revision -> {
                rejected(RejectionReason.RevisionMismatch(command.targetRevision, currentState.revision))
            }

            historyEntry == null -> {
                rejected(RejectionReason.NoRedoAvailable)
            }

            else -> {
                DocumentTransition.create(currentState, historyEntry.changeSet.patch)
            }
        }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}
