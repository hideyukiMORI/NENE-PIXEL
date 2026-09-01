package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryEntry
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal class UndoCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: UndoCommand,
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
                rejected(RejectionReason.NoUndoAvailable)
            }

            else -> {
                DocumentTransition.create(currentState, historyEntry.changeSet.inversePatch)
            }
        }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}
