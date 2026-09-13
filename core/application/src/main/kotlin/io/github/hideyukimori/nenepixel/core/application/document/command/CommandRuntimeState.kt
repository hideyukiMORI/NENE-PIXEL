package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public data class CommandRuntimeState internal constructor(
    public val documentState: DocumentState,
    public val historyAvailability: HistoryAvailability,
    internal val historyPosition: HistoryPosition,
    private val retention: HistoryRetentionSummary,
) {
    internal val historyEntryCount: Int get() = retention.entryCount
    internal val retainedHistoryChangeCount: Int get() = retention.changeCount
    internal val retainedHistoryByteCount: Long get() = retention.byteCount
}
