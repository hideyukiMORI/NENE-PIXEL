package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult

internal class BoundedLinearHistory private constructor(
    private val entries: List<HistoryEntry>,
    private val cursor: Int,
    private val basePosition: HistoryPosition,
    private val latestPosition: HistoryPosition,
    val retainedChangeCount: Int,
) {
    val entryCount: Int
        get() = entries.size

    val currentPosition: HistoryPosition
        get() = if (cursor == 0) basePosition else entries[cursor - 1].afterPosition

    val availability: HistoryAvailability
        get() =
            when {
                entries.isEmpty() -> HistoryAvailability.None
                cursor == 0 -> HistoryAvailability.RedoAvailable
                cursor == entries.size -> HistoryAvailability.UndoAvailable
                else -> HistoryAvailability.UndoAndRedoAvailable
            }

    val undoEntry: HistoryEntry?
        get() = entries.getOrNull(cursor - 1)

    val redoEntry: HistoryEntry?
        get() = entries.getOrNull(cursor)

    fun append(applied: CommandResult.Applied): HistoryAppendResult =
        when (val nextPosition = latestPosition.next()) {
            is HistoryPositionResult.Created -> {
                append(applied, nextPosition.position)
            }

            HistoryPositionResult.Exhausted -> {
                HistoryAppendResult.Rejected(HistoryAppendRejection.PositionExhausted)
            }
        }

    fun moveBackward(): BoundedLinearHistory {
        check(undoEntry != null) { "History cannot move backward without an undo entry." }
        return BoundedLinearHistory(entries, cursor - 1, basePosition, latestPosition, retainedChangeCount)
    }

    fun moveForward(): BoundedLinearHistory {
        check(redoEntry != null) { "History cannot move forward without a redo entry." }
        return BoundedLinearHistory(entries, cursor + 1, basePosition, latestPosition, retainedChangeCount)
    }

    private fun append(
        applied: CommandResult.Applied,
        afterPosition: HistoryPosition,
    ): HistoryAppendResult {
        val retainedPrefix = entries.take(cursor)
        val nextEntry = HistoryEntry.create(applied, currentPosition, afterPosition)
        val candidates = retainedPrefix + nextEntry
        return when (val retention = HistoryRetentionPolicy.retain(candidates.map(HistoryEntry::retainedChangeCount))) {
            is HistoryRetentionResult.Rejected -> {
                HistoryAppendResult.Rejected(
                    HistoryAppendRejection.EntryAboveRetainedChangeMaximum(
                        retention.rejection.attemptedCount,
                        retention.rejection.maximum,
                    ),
                )
            }

            is HistoryRetentionResult.Retained -> {
                val retainedEntries = candidates.drop(retention.evictedEntryCount)
                val nextBasePosition =
                    if (retention.evictedEntryCount == 0) {
                        basePosition
                    } else {
                        candidates[retention.evictedEntryCount - 1].afterPosition
                    }
                HistoryAppendResult.Appended(
                    history =
                        BoundedLinearHistory(
                            retainedEntries,
                            retainedEntries.size,
                            nextBasePosition,
                            afterPosition,
                            retention.retainedChangeCount,
                        ),
                )
            }
        }
    }

    companion object {
        fun empty(): BoundedLinearHistory =
            BoundedLinearHistory(
                entries = emptyList(),
                cursor = 0,
                basePosition = HistoryPosition.initial,
                latestPosition = HistoryPosition.initial,
                retainedChangeCount = 0,
            )
    }
}

internal sealed interface HistoryAppendResult {
    data class Appended(
        val history: BoundedLinearHistory,
    ) : HistoryAppendResult

    data class Rejected(
        val rejection: HistoryAppendRejection,
    ) : HistoryAppendResult
}

internal sealed interface HistoryAppendRejection {
    data class EntryAboveRetainedChangeMaximum(
        val attemptedCount: Int,
        val maximum: Int,
    ) : HistoryAppendRejection

    data object PositionExhausted : HistoryAppendRejection
}
