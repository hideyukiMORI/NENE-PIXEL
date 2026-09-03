package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HistoryRetentionPolicyTest {
    @Test
    fun `entry cap retains the newest bounded suffix`() {
        assertEquals(
            HistoryRetentionResult.Retained(0, PixelLimits.MAX_HISTORY_ENTRIES - 1),
            HistoryRetentionPolicy.retain(List(PixelLimits.MAX_HISTORY_ENTRIES - 1) { 1 }),
        )
        assertEquals(
            HistoryRetentionResult.Retained(0, PixelLimits.MAX_HISTORY_ENTRIES),
            HistoryRetentionPolicy.retain(List(PixelLimits.MAX_HISTORY_ENTRIES) { 1 }),
        )
        assertEquals(
            HistoryRetentionResult.Retained(1, PixelLimits.MAX_HISTORY_ENTRIES),
            HistoryRetentionPolicy.retain(List(PixelLimits.MAX_HISTORY_ENTRIES + 1) { 1 }),
        )
    }

    @Test
    fun `retained change cap evicts oldest entries deterministically`() {
        val fullCanvasChangeCount = PixelLimits.MAX_CANVAS_PIXELS

        assertEquals(
            HistoryRetentionResult.Retained(0, PixelLimits.MAX_RETAINED_CHANGES),
            HistoryRetentionPolicy.retain(List(8) { fullCanvasChangeCount }),
        )
        assertEquals(
            HistoryRetentionResult.Retained(1, PixelLimits.MAX_RETAINED_CHANGES),
            HistoryRetentionPolicy.retain(List(9) { fullCanvasChangeCount }),
        )
    }

    @Test
    fun `single entry above retained change cap is rejected with typed limit`() {
        assertEquals(
            HistoryRetentionResult.Rejected(
                HistoryRetentionRejection.EntryAboveRetainedChangeMaximum(
                    PixelLimits.MAX_RETAINED_CHANGES + 1,
                    PixelLimits.MAX_RETAINED_CHANGES,
                ),
            ),
            HistoryRetentionPolicy.retain(listOf(PixelLimits.MAX_RETAINED_CHANGES + 1)),
        )
    }
}
