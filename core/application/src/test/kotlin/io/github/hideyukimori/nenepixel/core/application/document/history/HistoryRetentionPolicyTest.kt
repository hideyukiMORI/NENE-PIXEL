package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HistoryRetentionPolicyTest {
    @Test
    fun `entry boundary is accepted through cap and rejected at cap plus one`() {
        assertEquals(
            HistoryRetentionResult.Accepted,
            HistoryRetentionPolicy.evaluate(PixelLimits.MAX_HISTORY_ENTRIES - 1, 0),
        )
        assertEquals(
            HistoryRetentionResult.Accepted,
            HistoryRetentionPolicy.evaluate(PixelLimits.MAX_HISTORY_ENTRIES, 0),
        )
        assertEquals(
            HistoryRetentionResult.Rejected(
                HistoryRetentionRejection.EntryCountAboveSupportedMaximum(
                    PixelLimits.MAX_HISTORY_ENTRIES + 1,
                    PixelLimits.MAX_HISTORY_ENTRIES,
                ),
            ),
            HistoryRetentionPolicy.evaluate(PixelLimits.MAX_HISTORY_ENTRIES + 1, 0),
        )
    }

    @Test
    fun `retained change boundary is accepted through cap and rejected at cap plus one`() {
        assertEquals(
            HistoryRetentionResult.Accepted,
            HistoryRetentionPolicy.evaluate(1, PixelLimits.MAX_RETAINED_CHANGES - 1),
        )
        assertEquals(
            HistoryRetentionResult.Accepted,
            HistoryRetentionPolicy.evaluate(1, PixelLimits.MAX_RETAINED_CHANGES),
        )
        assertEquals(
            HistoryRetentionResult.Rejected(
                HistoryRetentionRejection.ChangeCountAboveSupportedMaximum(
                    PixelLimits.MAX_RETAINED_CHANGES + 1,
                    PixelLimits.MAX_RETAINED_CHANGES,
                ),
            ),
            HistoryRetentionPolicy.evaluate(1, PixelLimits.MAX_RETAINED_CHANGES + 1),
        )
    }
}
