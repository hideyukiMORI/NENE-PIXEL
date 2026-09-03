package io.github.hideyukimori.nenepixel.core.application.document.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HistoryPositionTest {
    @Test
    fun `position advances monotonically and reports exhaustion as typed result`() {
        assertEquals(
            HistoryPositionResult.Created(HistoryPosition.create(1L)),
            HistoryPosition.initial.next(),
        )
        assertEquals(
            HistoryPositionResult.Exhausted,
            HistoryPosition.create(Long.MAX_VALUE).next(),
        )
    }
}
