package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EditorRecoveryOfferWorkflowTest {
    @Test
    fun `accepted recovery installs the candidate as dirty work and keeps its lineage`() =
        runBlocking {
            val fixture = candidateFixture()

            assertEquals(RecoveryStatus.UnadoptedCandidate, fixture.workflow.operation.value.recoveryStatus)
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.acceptRecovery())

            val state = fixture.runtime.state
            assertEquals(candidateDocument, state.documentState)
            assertEquals(DocumentDirtyState.Dirty, state.dirtyState)
            assertEquals(HistoryAvailability.None, state.historyAvailability)
            assertEquals(ViewportState.initial(candidateDocument.size), state.workspaceState.viewport)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertTrue(fixture.recovery.retireCalls.isEmpty())

            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Retired(generation(10)) }
            assertSaved(fixture.workflow.saveAs())
            assertEquals(listOf(ExpectedRecoveryLineage.Present(generation(9))), fixture.recovery.retireCalls)
        }

    @Test
    fun `accepted recovery resets the autosave capture of the replaced runtime`() =
        runBlocking {
            val fixture = candidateFixture()
            apply(fixture.runtime, position(0, 0), red)
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)

            val confirmation = assertAwaiting(fixture.workflow.acceptRecovery())
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.confirm(confirmation))

            assertNull(fixture.workflow.autosave.value.pendingRevision)
            assertEquals(AutosaveRequestResult.NoCapture, fixture.workflow.publishLatestCapture())
            assertTrue(fixture.recovery.publishCalls.isEmpty())
        }

    @Test
    fun `accepting from a dirty runtime asks to discard the current changes first`() =
        runBlocking {
            val fixture = candidateFixture()
            apply(fixture.runtime, position(0, 0), red)
            val before = fixture.runtime.state.documentState

            val confirmation = assertAwaiting(fixture.workflow.acceptRecovery())
            assertEquals(PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES, confirmation.reason)
            assertSame(before, fixture.runtime.state.documentState)

            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.confirm(confirmation))
            assertEquals(candidateDocument, fixture.runtime.state.documentState)
            assertEquals(DocumentDirtyState.Dirty, fixture.runtime.state.dirtyState)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }

    @Test
    fun `declining retires exactly the candidate generation and leaves the runtime alone`() =
        runBlocking {
            val fixture = candidateFixture()
            val before = fixture.runtime.state.documentState
            fixture.recovery.retireHandler = { expected ->
                RecoveryRetirementOutcome.Retired(nextGeneration(expected))
            }

            assertCompleted(PersistenceLastOutcome.RecoveryDeclined, fixture.workflow.declineRecovery())

            assertEquals(listOf(ExpectedRecoveryLineage.Present(generation(9))), fixture.recovery.retireCalls)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertSame(before, fixture.runtime.state.documentState)
            assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.declineRecovery())
        }

    @Test
    fun `a stale decline makes recovery unknown and reports the lineage change`() =
        runBlocking {
            val fixture = candidateFixture()
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Stale }

            val failure = assertFailed(fixture.workflow.declineRecovery())

            assertEquals(PersistenceFailure.RecoveryLineageChanged, failure)
            assertInstanceOf(RecoveryStatus.Unknown::class.java, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `accepting or declining without an offer is stale`() =
        runBlocking {
            val fixture = initializedFixture()

            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.acceptRecovery())
            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.declineRecovery())
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }

    @Test
    fun `an unadopted offer holds publication until the offer is resolved`() =
        runBlocking {
            val fixture = candidateFixture()
            apply(fixture.runtime, position(0, 0), red)

            assertEquals(AutosaveRequestResult.OfferPending, fixture.workflow.publishLatestCapture())

            assertTrue(fixture.recovery.publishCalls.isEmpty())
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            assertEquals(AutosaveLastOutcome.OfferPending, fixture.workflow.autosave.value.lastOutcome)
            assertEquals(RecoveryStatus.UnadoptedCandidate, fixture.workflow.operation.value.recoveryStatus)

            fixture.recovery.retireHandler = { expected ->
                RecoveryRetirementOutcome.Retired(nextGeneration(expected))
            }
            assertCompleted(PersistenceLastOutcome.RecoveryDeclined, fixture.workflow.declineRecovery())

            assertEquals(generation(11), assertPublished(fixture.workflow.publishLatestCapture()))
            val publication = fixture.recovery.publishCalls.single()
            assertEquals(ExpectedRecoveryLineage.Present(generation(10)), publication.expected)
        }
}

private val candidateDocument: DocumentState =
    state(canvas(2, 2), revision = revision(4), documentId = documentId('a'))

private suspend fun candidateFixture(): Fixture =
    Fixture(RecoveryInspection.Candidate(generation(9), candidateDocument)).also { it.initialize() }
