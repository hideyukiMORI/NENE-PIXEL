package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits

internal object HistoryRetentionPolicy {
    fun retain(changeCounts: List<Int>): HistoryRetentionResult {
        val newestChangeCount = changeCounts.lastOrNull() ?: 0
        if (newestChangeCount > PixelLimits.MAX_RETAINED_CHANGES) {
            return HistoryRetentionResult.Rejected(
                HistoryRetentionRejection.EntryAboveRetainedChangeMaximum(
                    newestChangeCount,
                    PixelLimits.MAX_RETAINED_CHANGES,
                ),
            )
        }
        var retainedChangeCount = changeCounts.sumOf { changeCount -> changeCount.toLong() }
        var evictedEntryCount = 0
        while (
            changeCounts.size - evictedEntryCount > PixelLimits.MAX_HISTORY_ENTRIES ||
            retainedChangeCount > PixelLimits.MAX_RETAINED_CHANGES
        ) {
            retainedChangeCount -= changeCounts[evictedEntryCount].toLong()
            evictedEntryCount += 1
        }
        return HistoryRetentionResult.Retained(evictedEntryCount, retainedChangeCount.toInt())
    }
}

internal sealed interface HistoryRetentionResult {
    data class Retained(
        val evictedEntryCount: Int,
        val retainedChangeCount: Int,
    ) : HistoryRetentionResult

    data class Rejected(
        val rejection: HistoryRetentionRejection.EntryAboveRetainedChangeMaximum,
    ) : HistoryRetentionResult
}

internal sealed interface HistoryRetentionRejection {
    data class EntryAboveRetainedChangeMaximum(
        val attemptedCount: Int,
        val maximum: Int,
    ) : HistoryRetentionRejection
}
