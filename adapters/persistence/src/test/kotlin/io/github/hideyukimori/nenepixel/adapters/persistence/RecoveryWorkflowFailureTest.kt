package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class RecoveryWorkflowFailureTest {
    @Test
    fun `write failure preserves verified C1 and the exact pending C2 can be retried`() =
        runBlocking { assertFailedRetry(RecoveryWorkflowFault.WRITE, RecoveryRetirementFailure.WRITE) }

    @Test
    fun `sync failure preserves verified C1 and the exact pending C2 can be retried`() =
        runBlocking { assertFailedRetry(RecoveryWorkflowFault.SYNC, RecoveryRetirementFailure.SYNC) }

    @Test
    fun `failed rollback leaves unknown lineage and cannot be retried by another publication request`() =
        runBlocking {
            val scenario =
                assertUncertainPublication(
                    RecoveryWorkflowFault.SYNC_AND_ROLLBACK,
                    RecoveryRetirementFailure.SYNC,
                    RecoveryRollbackOutcome.FAILED,
                )
            assertEquals(1, scenario.fixture.file.failCalls)
        }

    @Test
    fun `finish failure leaves unknown lineage without claiming rollback or publication`() =
        runBlocking {
            val scenario =
                assertUncertainPublication(
                    RecoveryWorkflowFault.FINISH,
                    RecoveryRetirementFailure.FINISH,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            assertEquals(0, scenario.fixture.file.failCalls)
        }

    @Test
    fun `read-back failure requires reinspection and offer resolution before pending C2 can publish`() =
        runBlocking {
            val scenario =
                assertUncertainPublication(
                    RecoveryWorkflowFault.READ_BACK,
                    RecoveryRetirementFailure.READ_BACK,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            val fixture = scenario.fixture
            assertEquals(0, fixture.file.failCalls)
            fixture.file.fault = RecoveryWorkflowFault.NONE
            fixture.assertCandidate(scenario.second.document, 3L)
            assertEquals(RecoveryInitializationResult.Ready, fixture.workflow.initializeRecovery())
            assertEquals(RecoveryStatus.UnadoptedCandidate, fixture.workflow.operation.value.recoveryStatus)
            val writesBeforeOffer = fixture.file.startCalls
            assertEquals(AutosaveRequestResult.OfferPending, fixture.workflow.publishLatestCapture())
            assertEquals(writesBeforeOffer, fixture.file.startCalls)
            assertEquals(scenario.second.token, fixture.workflow.autosave.value.pendingStateToken)
            val declined =
                assertInstanceOf(PersistenceRequestResult.Completed::class.java, fixture.workflow.declineRecovery())
            assertEquals(PersistenceLastOutcome.RecoveryDeclined, declined.outcome)
            assertEquals(RecoveryInspection.Retired(PersistenceTestValues.generation(4L)), fixture.adapter.inspect())
            assertEquals(scenario.second.token, fixture.workflow.autosave.value.pendingStateToken)
            fixture.publish(scenario.second, 5L)
            assertNull(fixture.workflow.autosave.value.pendingStateToken)
        }

    private suspend fun assertFailedRetry(
        fault: RecoveryWorkflowFault,
        failure: RecoveryRetirementFailure,
    ) {
        val fixture = RecoveryWorkflowFixture()
        val first = fixture.verifiedFirstCapture()
        val firstBytes = fixture.file.bytes
        val second = fixture.captureDuration(1, 260)
        fixture.file.fault = fault
        val result = assertInstanceOf(AutosaveRequestResult.Failed::class.java, fixture.workflow.publishLatestCapture())
        assertEquals(failure, result.failure)
        assertEquals(RecoveryRollbackOutcome.COMPLETED, result.rollback)
        val outcome =
            assertInstanceOf(AutosaveLastOutcome.Failed::class.java, fixture.workflow.autosave.value.lastOutcome)
        assertEquals(failure, outcome.failure)
        assertEquals(RecoveryRollbackOutcome.COMPLETED, outcome.rollback)
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
        assertEquals(AutosaveRequestResult.NoCapture, fixture.workflow.publishLatestCapture())
    }

    private suspend fun assertUncertainPublication(
        fault: RecoveryWorkflowFault,
        failure: RecoveryRetirementFailure,
        rollback: RecoveryRollbackOutcome,
    ): RecoveryWorkflowUncertainty {
        val fixture = RecoveryWorkflowFixture()
        val first = fixture.verifiedFirstCapture()
        val second = fixture.captureDuration(1, 260)
        fixture.file.fault = fault
        val result =
            assertInstanceOf(AutosaveRequestResult.Uncertain::class.java, fixture.workflow.publishLatestCapture())
        assertEquals(failure, result.failure)
        assertEquals(rollback, result.rollback)
        assertUncertainProjection(fixture, failure, rollback)
        assertEquals(second.document, fixture.runtime.state.documentState)
        assertEquals(second.token, fixture.workflow.autosave.value.pendingStateToken)
        assertNull(fixture.workflow.autosave.value.publishedStateToken)
        assertNull(fixture.workflow.autosave.value.publishingStateToken)
        assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        val writesBeforeRetry = fixture.file.startCalls
        assertEquals(AutosaveRequestResult.Unavailable, fixture.workflow.publishLatestCapture())
        assertEquals(writesBeforeRetry, fixture.file.startCalls)
        val completedWrite = fault == RecoveryWorkflowFault.READ_BACK
        assertStoredCandidate(fixture, if (completedWrite) second else first, if (completedWrite) 3L else 2L)
        return RecoveryWorkflowUncertainty(fixture, second)
    }

    private fun assertUncertainProjection(
        fixture: RecoveryWorkflowFixture,
        failure: RecoveryRetirementFailure,
        rollback: RecoveryRollbackOutcome,
    ) {
        val outcome =
            assertInstanceOf(AutosaveLastOutcome.Uncertain::class.java, fixture.workflow.autosave.value.lastOutcome)
        assertEquals(failure, outcome.failure)
        assertEquals(rollback, outcome.rollback)
        val status =
            assertInstanceOf(RecoveryStatus.Unknown::class.java, fixture.workflow.operation.value.recoveryStatus)
        val reason = assertInstanceOf(RecoveryUnavailableReason.RetirementUncertain::class.java, status.reason)
        assertEquals(failure, reason.failure)
        assertEquals(rollback, reason.rollback)
    }

    private fun assertStoredCandidate(
        fixture: RecoveryWorkflowFixture,
        capture: RecoveryWorkflowCapture,
        generation: Long,
    ) {
        val decoded =
            assertInstanceOf(RecoveryDecodeResult.Accepted::class.java, RecoveryRecordCodec.decode(fixture.file.bytes))
        assertEquals(
            RecoveryRecord.Candidate(
                PersistenceTestValues.generation(generation),
                DocumentImportSource.Current(capture.document),
            ),
            decoded.record,
        )
    }
}

private data class RecoveryWorkflowUncertainty(
    val fixture: RecoveryWorkflowFixture,
    val second: RecoveryWorkflowCapture,
)
