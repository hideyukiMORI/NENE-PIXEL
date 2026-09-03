package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandRuntimeState
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

internal class DocumentCleanCheckpoint private constructor(
    private val documentId: DocumentId,
    private val historyPosition: HistoryPosition,
) {
    fun deriveDirtyState(current: CommandRuntimeState): DocumentDirtyState =
        if (current.documentState.id == documentId && current.historyPosition == historyPosition) {
            DocumentDirtyState.Clean
        } else {
            DocumentDirtyState.Dirty
        }

    companion object {
        fun create(current: CommandRuntimeState): DocumentCleanCheckpoint =
            DocumentCleanCheckpoint(current.documentState.id, current.historyPosition)
    }
}
