package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentOutputStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PngExportWorkflowTest {
    @Test
    fun `stale export completion cannot clear a newer operation`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.pngExportOperations
            val first = assertInstanceOf(DocumentOutputStart.Started::class.java, operations.begin())
            operations.complete(first.handle, PngExportOutcome.Exported)
            val second = assertInstanceOf(DocumentOutputStart.Started::class.java, operations.begin())
            val before = fixture.workflow.operation.value
            assertEquals(PersistenceRequestResult.Stale, operations.complete(first.handle, PngExportOutcome.Cancelled))
            assertEquals(before, fixture.workflow.operation.value)
            assertCompleted(
                PersistenceLastOutcome.Cancelled,
                operations.complete(second.handle, PngExportOutcome.Cancelled),
            )
        }

    @Test
    fun `export never changes current owners checkpoint autosave or recovery`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val before = fixture.runtime.state
            val autosave = fixture.workflow.autosave.value
            assertCompleted(PersistenceLastOutcome.PngExported, fixture.workflow.exportPng())
            assertEquals(before, fixture.runtime.state)
            assertEquals(autosave, fixture.workflow.autosave.value)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
            assertTrue(fixture.recovery.publishCalls.isEmpty())
            assertEquals(listOf(before.documentState), fixture.exporter.documents)
        }

    @Test
    fun `export captures once while later editing stays dirty and other IO stays busy`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.exporter.handler = {
                gate.await()
                PngExportOutcome.Exported
            }
            val before = fixture.runtime.state.documentState
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.exportPng() }
            apply(fixture.runtime, position(0, 0), red)
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.saveAs())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.exportPng())
            val edited = fixture.runtime.state
            gate.complete(Unit)
            assertCompleted(PersistenceLastOutcome.PngExported, pending.await())
            assertEquals(listOf(before), fixture.exporter.documents)
            assertEquals(edited, fixture.runtime.state)
            assertEquals(DocumentDirtyState.Dirty, fixture.runtime.state.dirtyState)
        }

    @Test
    fun `late success after explicit cancellation is cancelled until transport drains`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.exporter.handler = {
                gate.await()
                PngExportOutcome.Exported
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.exportPng() }
            val phase =
                assertInstanceOf(
                    PersistenceOperationPhase.Exporting::class.java,
                    fixture.workflow.operation.value.phase,
                )
            assertEquals(PersistenceCancellationResult.CancellationStarted, fixture.workflow.cancel(phase.operation))
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.saveAs())
            gate.complete(Unit)
            assertCompleted(PersistenceLastOutcome.Cancelled, pending.await())
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `coroutine cancellation retains lease through noncancellable adapter cleanup`() =
        runBlocking {
            val fixture = initializedFixture()
            val cleanup = CompletableDeferred<Unit>()
            val cleanupStarted = CompletableDeferred<Unit>()
            fixture.exporter.handler = {
                try {
                    CompletableDeferred<Unit>().await()
                    PngExportOutcome.Exported
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        cleanup.await()
                    }
                }
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.exportPng() }
            pending.cancel()
            cleanupStarted.await()
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.exportPng())
            cleanup.complete(Unit)
            pending.cancelAndJoin()
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
        }

    @Test
    fun `export waits for active autosave then captures latest state`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val gate = CompletableDeferred<Unit>()
            fixture.recovery.publishHandler = {
                gate.await()
                RecoveryPublicationOutcome.Published(generation(1))
            }
            val publication = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.publishLatestCapture() }
            val export = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.exportPng() }
            apply(fixture.runtime, position(1, 0), green)
            val latest = fixture.runtime.state.documentState
            gate.complete(Unit)
            publication.await()
            assertCompleted(PersistenceLastOutcome.PngExported, export.await())
            assertEquals(listOf(latest), fixture.exporter.documents)
        }

    @Test
    fun `export failure is typed and preserves unadopted recovery`() =
        runBlocking {
            val source = initializedFixture().runtime.state.documentState
            val fixture = Fixture(RecoveryInspection.Candidate(generation(1), source))
            fixture.initialize()
            val before = fixture.runtime.state
            fixture.exporter.handler =
                { PngExportOutcome.Failed(ProjectStorageFailure.ReadBackMismatch, PartialOutputCleanup.DELETE_FAILED) }
            val failure =
                assertInstanceOf(PersistenceFailure.PngExport::class.java, assertFailed(fixture.workflow.exportPng()))
            assertEquals(PartialOutputCleanup.DELETE_FAILED, failure.cleanup)
            assertEquals(before, fixture.runtime.state)
            assertEquals(RecoveryStatus.UnadoptedCandidate, fixture.workflow.operation.value.recoveryStatus)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }
}
