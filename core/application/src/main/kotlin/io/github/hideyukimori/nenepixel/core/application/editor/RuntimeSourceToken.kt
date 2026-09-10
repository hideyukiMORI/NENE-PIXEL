package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

internal data class RuntimeSourceToken(
    val runtimeGeneration: Long,
    val documentId: DocumentId,
    val historyPosition: HistoryPosition,
)
