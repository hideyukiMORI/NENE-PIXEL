package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandRuntimeState
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

internal sealed interface DocumentCleanCheckpoint {
    fun deriveDirtyState(current: CommandRuntimeState): DocumentDirtyState

    data object Unsaved : DocumentCleanCheckpoint {
        override fun deriveDirtyState(current: CommandRuntimeState): DocumentDirtyState = DocumentDirtyState.Dirty
    }

    class Recorded(
        private val documentId: DocumentId,
        private val historyPosition: HistoryPosition,
    ) : DocumentCleanCheckpoint {
        override fun deriveDirtyState(current: CommandRuntimeState): DocumentDirtyState =
            if (current.documentState.id == documentId && current.historyPosition == historyPosition) {
                DocumentDirtyState.Clean
            } else {
                DocumentDirtyState.Dirty
            }
    }

    companion object {
        fun create(
            documentId: DocumentId,
            historyPosition: HistoryPosition,
        ): DocumentCleanCheckpoint = Recorded(documentId, historyPosition)

        fun create(current: CommandRuntimeState): DocumentCleanCheckpoint =
            Recorded(current.documentState.id, current.historyPosition)
    }
}
