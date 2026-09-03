package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits

internal object HistoryRetentionPolicy {
    fun evaluate(
        entryCount: Int,
        retainedChangeCount: Int,
    ): HistoryRetentionResult =
        when {
            entryCount > PixelLimits.MAX_HISTORY_ENTRIES -> {
                HistoryRetentionResult.Rejected(
                    HistoryRetentionRejection.EntryCountAboveSupportedMaximum(
                        entryCount,
                        PixelLimits.MAX_HISTORY_ENTRIES,
                    ),
                )
            }

            retainedChangeCount > PixelLimits.MAX_RETAINED_CHANGES -> {
                HistoryRetentionResult.Rejected(
                    HistoryRetentionRejection.ChangeCountAboveSupportedMaximum(
                        retainedChangeCount,
                        PixelLimits.MAX_RETAINED_CHANGES,
                    ),
                )
            }

            else -> {
                HistoryRetentionResult.Accepted
            }
        }
}

internal sealed interface HistoryRetentionResult {
    data object Accepted : HistoryRetentionResult

    data class Rejected(
        val rejection: HistoryRetentionRejection,
    ) : HistoryRetentionResult
}

internal sealed interface HistoryRetentionRejection {
    data class EntryCountAboveSupportedMaximum(
        val attemptedCount: Int,
        val maximum: Int,
    ) : HistoryRetentionRejection

    data class ChangeCountAboveSupportedMaximum(
        val attemptedCount: Int,
        val maximum: Int,
    ) : HistoryRetentionRejection
}
