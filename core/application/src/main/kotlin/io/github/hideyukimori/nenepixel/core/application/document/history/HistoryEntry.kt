package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet

internal class HistoryEntry private constructor(
    val changeSet: ChangeSet,
    val beforePosition: HistoryPosition,
    val afterPosition: HistoryPosition,
) {
    val retainedChangeCount: Int
        get() = changeSet.retainedChangeCount

    companion object {
        fun create(
            applied: CommandResult.Applied,
            beforePosition: HistoryPosition,
            afterPosition: HistoryPosition,
        ): HistoryEntry = HistoryEntry(applied.changeSet, beforePosition, afterPosition)
    }
}
