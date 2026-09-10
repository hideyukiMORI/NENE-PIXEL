package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EditorAutosaveWorkflowTest {
    @Test
    fun `committed commands coalesce into one latest capture that a request publishes`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            apply(fixture.runtime, position(1, 0), green)
            apply(fixture.runtime, position(2, 0), red)

            assertEquals(3L, fixture.workflow.autosave.value.pendingRevision)
            assertNull(fixture.workflow.autosave.value.publishedRevision)

            assertEquals(generation(1), assertPublished(fixture.workflow.publishLatestCapture()))

            val publication = fixture.recovery.publishCalls.single()
            assertEquals(ExpectedRecoveryLineage.Missing, publication.expected)
            assertEquals(3L, publication.document.revision.value)
            assertNull(fixture.workflow.autosave.value.pendingRevision)
            assertEquals(3L, fixture.workflow.autosave.value.publishedRevision)
            assertFalse(fixture.workflow.autosave.value.publishing)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(AutosaveRequestResult.NoCapture, fixture.workflow.publishLatestCapture())
            assertEquals(1, fixture.recovery.publishCalls.size)
        }

    @Test
    fun `a commit during a publication stays pending and is published by the next request`() =
        runBlocking {
            val fixture = initializedFixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.recovery.publishHandler = { publication ->
                entered.complete(Unit)
                release.await()
                RecoveryPublicationOutcome.Published(nextGeneration(publication.expected))
            }
            apply(fixture.runtime, position(0, 0), red)

            val publishing = async { fixture.workflow.publishLatestCapture() }
            entered.await()
            assertTrue(fixture.workflow.autosave.value.publishing)
            apply(fixture.runtime, position(1, 0), green)
            assertEquals(2L, fixture.workflow.autosave.value.pendingRevision)
            release.complete(Unit)

            assertEquals(generation(1), assertPublished(publishing.await()))
            assertEquals(1L, fixture.workflow.autosave.value.publishedRevision)
            assertEquals(2L, fixture.workflow.autosave.value.pendingRevision)

            assertEquals(generation(2), assertPublished(fixture.workflow.publishLatestCapture()))
            assertEquals(2, fixture.recovery.publishCalls.size)
            val second = fixture.recovery.publishCalls[1]
            assertEquals(ExpectedRecoveryLineage.Present(generation(1)), second.expected)
            assertEquals(2L, second.document.revision.value)
        }

    @Test
    fun `a user operation waits for the active publication and then runs`() =
        runBlocking {
            val fixture = initializedFixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val saveEntered = CompletableDeferred<Unit>()
            fixture.recovery.publishHandler = { publication ->
                entered.complete(Unit)
                release.await()
                RecoveryPublicationOutcome.Published(nextGeneration(publication.expected))
            }
            fixture.storage.saveHandler = {
                saveEntered.complete(Unit)
                ProjectSaveOutcome.Saved
            }
            fixture.recovery.retireHandler = { expected ->
                RecoveryRetirementOutcome.Retired(nextGeneration(expected))
            }
            apply(fixture.runtime, position(0, 0), red)

            val publishing = async { fixture.workflow.publishLatestCapture() }
            entered.await()
            val saving = async { fixture.workflow.saveAs() }
            yield()
            assertFalse(saveEntered.isCompleted)

            release.complete(Unit)
            assertEquals(generation(1), assertPublished(publishing.await()))
            val saved = assertSaved(saving.await())
            assertEquals(generation(2), assertRetiredCleanup(saved.recoveryCleanup))
            assertTrue(saveEntered.isCompleted)
        }

    @Test
    fun `an autosave request during a user operation is deferred and keeps the capture`() =
        runBlocking {
            val fixture = initializedFixture()
            val saveEntered = CompletableDeferred<Unit>()
            val releaseSave = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                saveEntered.complete(Unit)
                releaseSave.await()
                ProjectSaveOutcome.Saved
            }
            apply(fixture.runtime, position(0, 0), red)

            val saving = async { fixture.workflow.saveAs() }
            saveEntered.await()

            assertEquals(AutosaveRequestResult.Deferred, fixture.workflow.publishLatestCapture())
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            assertEquals(AutosaveLastOutcome.Deferred, fixture.workflow.autosave.value.lastOutcome)
            assertTrue(fixture.recovery.publishCalls.isEmpty())

            releaseSave.complete(Unit)
            assertSaved(saving.await())
            assertNull(fixture.workflow.autosave.value.pendingRevision)
        }

    @Test
    fun `verified save drops the capture at the saved revision and keeps a newer one`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }
            apply(fixture.runtime, position(0, 0), red)

            assertSaved(fixture.workflow.saveAs())
            assertNull(fixture.workflow.autosave.value.pendingRevision)

            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                entered.complete(Unit)
                release.await()
                ProjectSaveOutcome.Saved
            }
            apply(fixture.runtime, position(1, 0), green)
            val saving = async { fixture.workflow.saveAs() }
            entered.await()
            apply(fixture.runtime, position(2, 0), red)
            release.complete(Unit)

            assertSaved(saving.await())
            assertEquals(3L, fixture.workflow.autosave.value.pendingRevision)
            assertTrue(fixture.recovery.publishCalls.isEmpty())
        }

    @Test
    fun `a stale publication makes recovery unknown and blocks further publication`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.recovery.publishHandler = { RecoveryPublicationOutcome.Stale }
            apply(fixture.runtime, position(0, 0), red)

            assertEquals(AutosaveRequestResult.Stale, fixture.workflow.publishLatestCapture())

            assertEquals(AutosaveLastOutcome.Stale, fixture.workflow.autosave.value.lastOutcome)
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            assertInstanceOf(RecoveryStatus.Unknown::class.java, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(AutosaveRequestResult.Unavailable, fixture.workflow.publishLatestCapture())
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            assertEquals(1, fixture.recovery.publishCalls.size)
        }

    @Test
    fun `a failed publication keeps the capture and a later request republishes it`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.recovery.publishHandler = {
                RecoveryPublicationOutcome.Failed(
                    RecoveryRetirementFailure.WRITE,
                    RecoveryRollbackOutcome.COMPLETED,
                )
            }
            apply(fixture.runtime, position(0, 0), red)

            assertEquals(
                AutosaveRequestResult.Failed(RecoveryRetirementFailure.WRITE, RecoveryRollbackOutcome.COMPLETED),
                fixture.workflow.publishLatestCapture(),
            )
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            assertNull(fixture.workflow.autosave.value.publishedRevision)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)

            fixture.recovery.publishHandler = { publication ->
                RecoveryPublicationOutcome.Published(nextGeneration(publication.expected))
            }
            assertEquals(generation(1), assertPublished(fixture.workflow.publishLatestCapture()))
            assertEquals(1L, fixture.workflow.autosave.value.publishedRevision)
        }

    @Test
    fun `an uncertain publication makes recovery unknown and keeps the capture`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.recovery.publishHandler = {
                RecoveryPublicationOutcome.Uncertain(
                    RecoveryRetirementFailure.READ_BACK,
                    RecoveryRollbackOutcome.FAILED,
                )
            }
            apply(fixture.runtime, position(0, 0), red)

            assertEquals(
                AutosaveRequestResult.Uncertain(RecoveryRetirementFailure.READ_BACK, RecoveryRollbackOutcome.FAILED),
                fixture.workflow.publishLatestCapture(),
            )
            assertEquals(
                AutosaveLastOutcome.Uncertain(RecoveryRetirementFailure.READ_BACK, RecoveryRollbackOutcome.FAILED),
                fixture.workflow.autosave.value.lastOutcome,
            )
            assertInstanceOf(RecoveryStatus.Unknown::class.java, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
        }

    @Test
    fun `a switch resets the capture so no other runtime capture is republished`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Retired(generation(1)) }

            val confirmation = assertAwaiting(fixture.workflow.createNewDocument(newRequest(3, 2)))
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.confirm(confirmation))

            assertNull(fixture.workflow.autosave.value.pendingRevision)
            assertNull(fixture.workflow.autosave.value.publishedRevision)
            assertEquals(AutosaveRequestResult.NoCapture, fixture.workflow.publishLatestCapture())
            assertTrue(fixture.recovery.publishCalls.isEmpty())
        }

    @Test
    fun `publication is unavailable until recovery initialization completes`() =
        runBlocking {
            val fixture = Fixture()
            apply(fixture.runtime, position(0, 0), red)

            assertEquals(AutosaveRequestResult.Unavailable, fixture.workflow.publishLatestCapture())
            assertEquals(1L, fixture.workflow.autosave.value.pendingRevision)
            assertTrue(fixture.recovery.publishCalls.isEmpty())

            fixture.initialize()
            assertEquals(generation(1), assertPublished(fixture.workflow.publishLatestCapture()))
        }

    @Test
    fun `caller cancellation cannot split a durable publication from its runtime record`() =
        runBlocking {
            val fixture = initializedFixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.recovery.publishHandler = { publication ->
                entered.complete(Unit)
                release.await()
                RecoveryPublicationOutcome.Published(nextGeneration(publication.expected))
            }
            apply(fixture.runtime, position(0, 0), red)

            val publishing = async { fixture.workflow.publishLatestCapture() }
            entered.await()
            publishing.cancel()
            release.complete(Unit)
            publishing.join()

            assertEquals(1L, fixture.workflow.autosave.value.publishedRevision)
            assertNull(fixture.workflow.autosave.value.pendingRevision)
            assertFalse(fixture.workflow.autosave.value.publishing)
            assertEquals(
                AutosaveLastOutcome.Published(generation(1)),
                fixture.workflow.autosave.value.lastOutcome,
            )
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)

            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }
            assertSaved(fixture.workflow.saveAs())
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `a commit that returns to the published revision clears the pending capture`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            assertEquals(generation(1), assertPublished(fixture.workflow.publishLatestCapture()))

            apply(fixture.runtime, position(1, 0), green)
            assertEquals(2L, fixture.workflow.autosave.value.pendingRevision)
            undo(fixture.runtime)

            assertNull(fixture.workflow.autosave.value.pendingRevision)
            assertEquals(1L, fixture.workflow.autosave.value.publishedRevision)
            assertEquals(AutosaveRequestResult.NoCapture, fixture.workflow.publishLatestCapture())
            assertEquals(1, fixture.recovery.publishCalls.size)
        }

    @Test
    fun `undo records its committed revision as the latest capture`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            assertEquals(generation(1), assertPublished(fixture.workflow.publishLatestCapture()))

            undo(fixture.runtime)

            assertEquals(0L, fixture.workflow.autosave.value.pendingRevision)
            assertEquals(generation(2), assertPublished(fixture.workflow.publishLatestCapture()))
            val undone = fixture.recovery.publishCalls[1]
            assertEquals(0L, undone.document.revision.value)
        }
}
