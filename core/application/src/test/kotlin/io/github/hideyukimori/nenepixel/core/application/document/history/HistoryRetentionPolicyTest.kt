package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HistoryRetentionPolicyTest {
    @Test
    fun `entry cap retains newest suffix and all three budgets`() {
        val entry = HistoryPayload(changeCount = 1, byteCount = 8)
        assertEquals(
            HistoryRetentionResult.Retained(0, PixelLimits.MAX_HISTORY_ENTRIES, 8L * PixelLimits.MAX_HISTORY_ENTRIES),
            HistoryRetentionPolicy.retain(List(PixelLimits.MAX_HISTORY_ENTRIES) { entry }),
        )
        assertEquals(
            HistoryRetentionResult.Retained(1, PixelLimits.MAX_HISTORY_ENTRIES, 8L * PixelLimits.MAX_HISTORY_ENTRIES),
            HistoryRetentionPolicy.retain(List(PixelLimits.MAX_HISTORY_ENTRIES + 1) { entry }),
        )
    }

    @Test
    fun `change and byte caps independently evict oldest entries`() {
        val fullCanvas = HistoryPayload(PixelLimits.MAX_CANVAS_PIXELS, 1)
        assertEquals(
            HistoryRetentionResult.Retained(1, PixelLimits.MAX_RETAINED_CHANGES, 8),
            HistoryRetentionPolicy.retain(List(9) { fullCanvas }),
        )

        val halfBytes = HistoryPayload(1, PixelLimits.MAX_RETAINED_PAYLOAD_BYTES / 2)
        assertEquals(
            HistoryRetentionResult.Retained(1, 2, PixelLimits.MAX_RETAINED_PAYLOAD_BYTES),
            HistoryRetentionPolicy.retain(List(3) { halfBytes }),
        )
    }

    @Test
    fun `unreachable single entry limits still reject as policy contracts`() {
        assertEquals(
            HistoryRetentionResult.Rejected(
                HistoryAppendRejection.EntryAboveRetainedChangeMaximum(
                    PixelLimits.MAX_RETAINED_CHANGES + 1,
                    PixelLimits.MAX_RETAINED_CHANGES,
                ),
            ),
            HistoryRetentionPolicy.retain(
                listOf(HistoryPayload(PixelLimits.MAX_RETAINED_CHANGES + 1, 0)),
            ),
        )
        assertEquals(
            HistoryRetentionResult.Rejected(
                HistoryAppendRejection.EntryAboveRetainedPayloadMaximum(
                    PixelLimits.MAX_RETAINED_PAYLOAD_BYTES + 1,
                    PixelLimits.MAX_RETAINED_PAYLOAD_BYTES,
                ),
            ),
            HistoryRetentionPolicy.retain(
                listOf(HistoryPayload(0, PixelLimits.MAX_RETAINED_PAYLOAD_BYTES + 1)),
            ),
        )
    }
}
