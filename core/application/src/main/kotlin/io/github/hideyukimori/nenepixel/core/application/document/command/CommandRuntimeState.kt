package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public data class CommandRuntimeState internal constructor(
    public val documentState: DocumentState,
    public val historyAvailability: HistoryAvailability,
    internal val historyPosition: HistoryPosition,
    internal val historyEntryCount: Int,
    internal val retainedHistoryChangeCount: Int,
)
