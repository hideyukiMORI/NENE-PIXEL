package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RecoveryWorkflowCancellationTest {
    @Test
    fun `caller cancellation completes the held capture and preserves the newer pending capture`() =
        runBlocking {
            val fixture = RecoveryWorkflowFixture()
            val first = fixture.verifiedFirstCapture()
            val second = fixture.captureDuration(1, 260)
            val gate = RecoveryWorkflowWriteGate()
            fixture.file.writeGate = gate
            val caller = async { fixture.workflow.publishLatestCapture() }
            try {
                gate.awaitEntered()
                assertEquals(second.token, fixture.workflow.autosave.value.publishingStateToken)
                assertEquals(AutosaveRequestResult.Deferred, fixture.workflow.publishLatestCapture())
                caller.cancel()
                val newer = fixture.captureDuration(0, 120)
                assertNotEquals(first.token, second.token)
                assertNotEquals(second.token, newer.token)
                assertFalse(caller.isCompleted)
                gate.release()
                awaitCaller(caller)
                assertTrue(caller.isCancelled)
                assertCompletedCapture(fixture, second)
                assertEquals(newer.token, fixture.workflow.autosave.value.pendingStateToken)
                fixture.assertCandidate(second.document, 3L)
                fixture.file.writeGate = null
                fixture.publish(newer, 4L)
                assertNull(fixture.workflow.autosave.value.pendingStateToken)
            } finally {
                gate.release()
                awaitCaller(caller)
                fixture.file.writeGate = null
            }
        }

    @Test
    fun `write cancellation rolls back rethrows and releases the lease for the same capture`() =
        runBlocking {
            val fixture = RecoveryWorkflowFixture()
            val first = fixture.verifiedFirstCapture()
            val firstBytes = fixture.file.bytes
            val second = fixture.captureDuration(1, 260)
            fixture.file.fault = RecoveryWorkflowFault.WRITE_CANCELLATION

            val failure =
                try {
                    fixture.workflow.publishLatestCapture()
                    null
                } catch (cancelled: CancellationException) {
                    cancelled
                }
            assertInstanceOf(CancellationException::class.java, failure)

            assertEquals(1, fixture.file.failCalls)
            assertArrayEquals(firstBytes, fixture.file.bytes)
            assertEquals(second.document, fixture.runtime.state.documentState)
            assertEquals(second.token, fixture.workflow.autosave.value.pendingStateToken)
            assertEquals(first.token, fixture.workflow.autosave.value.publishedStateToken)
            assertNull(fixture.workflow.autosave.value.publishingStateToken)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
            fixture.assertCandidate(first.document, 2L)
            fixture.file.fault = RecoveryWorkflowFault.NONE
            fixture.publish(second, 3L)
            assertNull(fixture.workflow.autosave.value.pendingStateToken)
        }

    private fun assertCompletedCapture(
        fixture: RecoveryWorkflowFixture,
        capture: RecoveryWorkflowCapture,
    ) {
        val autosave = fixture.workflow.autosave.value
        assertEquals(capture.token, autosave.publishedStateToken)
        assertNull(autosave.publishingStateToken)
        val outcome = assertInstanceOf(AutosaveLastOutcome.Published::class.java, autosave.lastOutcome)
        assertEquals(PersistenceTestValues.generation(3L), outcome.generation)
        assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
        assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
    }

    private suspend fun awaitCaller(caller: Job) {
        withTimeout(COMPLETION_WAIT_MILLIS) { caller.join() }
    }

    private companion object {
        const val COMPLETION_WAIT_MILLIS: Long = 5_000L
    }
}
