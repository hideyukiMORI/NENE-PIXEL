package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet

internal class HistoryEntry private constructor(
    val changeSet: ChangeSet,
) {
    companion object {
        fun create(applied: CommandResult.Applied): HistoryEntry = HistoryEntry(applied.changeSet)
    }
}
