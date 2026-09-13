package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits

internal object HistoryRetentionPolicy {
    fun retain(payloads: List<HistoryPayload>): HistoryRetentionResult {
        val newest = payloads.lastOrNull() ?: HistoryPayload(0, 0L)
        return when {
            newest.changeCount > PixelLimits.MAX_RETAINED_CHANGES -> {
                HistoryRetentionResult.Rejected(
                    HistoryAppendRejection.EntryAboveRetainedChangeMaximum(
                        newest.changeCount,
                        PixelLimits.MAX_RETAINED_CHANGES,
                    ),
                )
            }

            newest.byteCount > PixelLimits.MAX_RETAINED_PAYLOAD_BYTES -> {
                HistoryRetentionResult.Rejected(
                    HistoryAppendRejection.EntryAboveRetainedPayloadMaximum(
                        newest.byteCount,
                        PixelLimits.MAX_RETAINED_PAYLOAD_BYTES,
                    ),
                )
            }

            else -> {
                retainedSuffix(payloads)
            }
        }
    }

    private fun retainedSuffix(payloads: List<HistoryPayload>): HistoryRetentionResult.Retained {
        var retainedChanges = payloads.sumOf { it.changeCount.toLong() }
        var retainedBytes = payloads.sumOf { it.byteCount }
        var evicted = 0
        while (
            payloads.size - evicted > PixelLimits.MAX_HISTORY_ENTRIES ||
            retainedChanges > PixelLimits.MAX_RETAINED_CHANGES ||
            retainedBytes > PixelLimits.MAX_RETAINED_PAYLOAD_BYTES
        ) {
            retainedChanges -= payloads[evicted].changeCount
            retainedBytes -= payloads[evicted].byteCount
            evicted += 1
        }
        return HistoryRetentionResult.Retained(evicted, retainedChanges.toInt(), retainedBytes)
    }
}

internal data class HistoryPayload(
    val changeCount: Int,
    val byteCount: Long,
)

internal sealed interface HistoryRetentionResult {
    data class Retained(
        val evictedEntryCount: Int,
        val retainedChangeCount: Int,
        val retainedByteCount: Long,
    ) : HistoryRetentionResult

    data class Rejected(
        val rejection: HistoryAppendRejection,
    ) : HistoryRetentionResult
}
