package io.github.hideyukimori.nenepixel.core.application.persistence

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PersistenceAutosaveFlowTest {
    @Test
    fun `busy attempt retries once when the publication has already completed`() =
        runBlocking {
            val fixture = Fixture()
            val flow = PersistenceAutosaveFlow(fixture.runtime.autosaveOperations, fixture.recovery)
            var attempts = 0

            val result =
                flow.retryAfterPublication {
                    attempts += 1
                    if (attempts == 1) PersistenceRequestResult.Busy else PersistenceRequestResult.RecoveryUnavailable
                }

            assertEquals(PersistenceRequestResult.RecoveryUnavailable, result)
            assertEquals(2, attempts)
        }

    @Test
    fun `busy user operation remains busy after one bounded retry`() =
        runBlocking {
            val fixture = Fixture()
            val flow = PersistenceAutosaveFlow(fixture.runtime.autosaveOperations, fixture.recovery)
            var attempts = 0

            val result =
                flow.retryAfterPublication {
                    attempts += 1
                    PersistenceRequestResult.Busy
                }

            assertEquals(PersistenceRequestResult.Busy, result)
            assertEquals(2, attempts)
        }
}
