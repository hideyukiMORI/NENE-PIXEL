package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntimeState
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class EditorPersistenceWorkflowTest {
    @Test
    fun `recovery inspection gates destructive work and can recover from failure`() =
        runBlocking {
            val fixture = Fixture()
            assertEquals(PersistenceOperationPhase.Initializing, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceRequestResult.RecoveryUnavailable, fixture.workflow.saveAs())
            assertEquals(PersistenceRequestResult.RecoveryUnavailable, fixture.workflow.load())

            fixture.recovery.inspection = RecoveryInspection.Failed(RecoveryInspectionFailure.CORRUPT)
            val failed = fixture.workflow.initializeRecovery()
            assertEquals(RecoveryInspectionFailure.CORRUPT, assertFailedInspection(failed))
            assertInstanceOf(RecoveryStatus.Unknown::class.java, fixture.workflow.operation.value.recoveryStatus)
            assertEquals(
                PersistenceRequestResult.RecoveryUnavailable,
                fixture.workflow.createNewDocument(newRequest(2, 2)),
            )

            fixture.recovery.inspection = RecoveryInspection.Retired(generation(7))
            assertEquals(RecoveryInitializationResult.Ready, fixture.workflow.initializeRecovery())
            assertEquals(RecoveryInitializationResult.AlreadyReady, fixture.workflow.initializeRecovery())
            assertEquals(2, fixture.recovery.inspectCalls)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
        }

    @Test
    fun `verified save checkpoints the captured history position and retires its captured lineage`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val capturedState = fixture.runtime.state.documentState
            val saveStarted = CompletableDeferred<DocumentState>()
            val finishSave = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = { document ->
                saveStarted.complete(document)
                finishSave.await()
                ProjectSaveOutcome.Saved
            }
            fixture.recovery.retireHandler = { expected ->
                assertEquals(ExpectedRecoveryLineage.Missing, expected)
                RecoveryRetirementOutcome.Retired(generation(1))
            }

            val result = async { fixture.workflow.saveAs() }
            assertSame(capturedState, saveStarted.await())
            apply(fixture.runtime, position(1, 0), green)
            finishSave.complete(Unit)

            val saved = assertSaved(result.await())
            assertEquals(generation(1), assertRetiredCleanup(saved.recoveryCleanup))
            assertEquals(DocumentDirtyState.Dirty, fixture.runtime.state.dirtyState)
            undo(fixture.runtime)
            assertEquals(capturedState, fixture.runtime.state.documentState)
            assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
            assertEquals(listOf(ExpectedRecoveryLineage.Missing), fixture.recovery.retireCalls)
        }

    @Test
    fun `failed save reports partial output cleanup and preserves dirty state`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            fixture.storage.saveHandler = {
                ProjectSaveOutcome.Failed(
                    ProjectStorageFailure.IoFailure(ProjectTransportPhase.DESTINATION_WRITE),
                    PartialOutputCleanup.DELETE_FAILED,
                )
            }

            val failure = assertFailed(fixture.workflow.saveAs())
            val storage = assertInstanceOf(PersistenceFailure.Storage::class.java, failure)
            assertEquals(
                ProjectStorageFailure.IoFailure(ProjectTransportPhase.DESTINATION_WRITE),
                storage.failure,
            )
            assertEquals(PartialOutputCleanup.DELETE_FAILED, storage.cleanup)
            assertEquals(DocumentDirtyState.Dirty, fixture.runtime.state.dirtyState)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }

    @Test
    fun `save preserves an unadopted recovery candidate`() =
        runBlocking {
            val recoveryDocument = state(canvas(2, 2), documentId = documentId('a'))
            val fixture = Fixture(RecoveryInspection.Candidate(generation(9), recoveryDocument))
            fixture.initialize()
            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }

            val saved = assertSaved(fixture.workflow.saveAs())

            assertEquals(RecoveryCleanupOutcome.PreservedUnadoptedCandidate, saved.recoveryCleanup)
            assertEquals(RecoveryStatus.UnadoptedCandidate, fixture.workflow.operation.value.recoveryStatus)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }

    @Test
    fun `new document requires candidate-discard consent and retires that exact generation`() =
        runBlocking {
            val recoveryDocument = state(canvas(2, 2), documentId = documentId('a'))
            val fixture = Fixture(RecoveryInspection.Candidate(generation(9), recoveryDocument))
            fixture.initialize()
            val confirmation = assertAwaiting(fixture.workflow.createNewDocument(newRequest(3, 2)))
            assertEquals(PersistenceConfirmationReason.DISCARD_RECOVERY_CANDIDATE, confirmation.reason)
            fixture.recovery.retireHandler = { expected ->
                assertEquals(ExpectedRecoveryLineage.Present(generation(9)), expected)
                RecoveryRetirementOutcome.Retired(generation(10))
            }

            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.confirm(confirmation))
            assertEquals(listOf(ExpectedRecoveryLineage.Present(generation(9))), fixture.recovery.retireCalls)
            assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
        }

    @Test
    fun `external cancellation retains the transport lease until unwind and releases it afterward`() =
        runBlocking {
            val fixture = initializedFixture()
            val saveEntered = CompletableDeferred<Unit>()
            val releaseSave = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                saveEntered.complete(Unit)
                withContext(NonCancellable) { releaseSave.await() }
                ProjectSaveOutcome.Saved
            }

            val saving = async { fixture.workflow.saveAs() }
            saveEntered.await()
            val handle = assertSaving(fixture.workflow.operation.value.phase)
            assertEquals(PersistenceCancellationResult.CancellationStarted, fixture.workflow.cancel(handle))
            saving.cancel()
            assertInstanceOf(PersistenceOperationPhase.Cancelling::class.java, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())

            releaseSave.complete(Unit)
            saving.join()
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)

            fixture.storage.saveHandler = { ProjectSaveOutcome.Cancelled }
            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.saveAs())
        }

    @Test
    fun `direct caller job cancellation releases a matching transport after the port unwinds`() =
        runBlocking {
            val fixture = initializedFixture()
            val saveEntered = CompletableDeferred<Unit>()
            val neverCompletes = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                saveEntered.complete(Unit)
                neverCompletes.await()
                ProjectSaveOutcome.Saved
            }

            val saving = async { fixture.workflow.saveAs() }
            saveEntered.await()
            saving.cancelAndJoin()

            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
            fixture.storage.loadHandler = { ProjectLoadOutcome.Cancelled }
            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.load())
        }

    @Test
    fun `operation handles from another runtime are stale even when counters match`() =
        runBlocking {
            val first = initializedFixture()
            val second = initializedFixture()
            val firstEntered = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()
            val firstRelease = CompletableDeferred<Unit>()
            val secondRelease = CompletableDeferred<Unit>()
            first.storage.loadHandler = {
                firstEntered.complete(Unit)
                firstRelease.await()
                ProjectLoadOutcome.Cancelled
            }
            second.storage.loadHandler = {
                secondEntered.complete(Unit)
                secondRelease.await()
                ProjectLoadOutcome.Cancelled
            }
            val firstJob = async { first.workflow.load() }
            val secondJob = async { second.workflow.load() }
            firstEntered.await()
            secondEntered.await()
            val firstHandle = assertLoading(first.workflow.operation.value.phase)
            val secondHandle = assertLoading(second.workflow.operation.value.phase)

            assertEquals(PersistenceCancellationResult.Stale, second.workflow.cancel(firstHandle))
            assertEquals(PersistenceCancellationResult.Stale, first.workflow.cancel(secondHandle))

            firstRelease.complete(Unit)
            secondRelease.complete(Unit)
            assertCompleted(PersistenceLastOutcome.Cancelled, firstJob.await())
            assertCompleted(PersistenceLastOutcome.Cancelled, secondJob.await())
        }

    @Test
    fun `load accepts an edit undone to the exact starting position`() =
        runBlocking {
            val fixture = initializedFixture()
            val loaded =
                state(
                    canvas(3, 2),
                    revision = revision(5),
                    pixels = listOf(red, green, red, green, red, green),
                    documentId = documentId('b'),
                )
            val loadEntered = CompletableDeferred<Unit>()
            val releaseLoad = CompletableDeferred<Unit>()
            fixture.storage.loadHandler = {
                loadEntered.complete(Unit)
                releaseLoad.await()
                ProjectLoadOutcome.Loaded(loaded)
            }
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Retired(generation(1)) }

            val result = async { fixture.workflow.load() }
            loadEntered.await()
            apply(fixture.runtime, position(0, 0), red)
            undo(fixture.runtime)
            assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
            releaseLoad.complete(Unit)

            assertCompleted(PersistenceLastOutcome.Loaded, result.await())
            assertEquals(loaded, fixture.runtime.state.documentState)
            assertEquals(HistoryAvailability.None, fixture.runtime.state.historyAvailability)
            assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
        }

    @Test
    fun `load requires fresh consent after a replacement branch changes the exact position`() =
        runBlocking {
            val fixture = initializedFixture()
            val loaded = state(canvas(3, 2), documentId = documentId('b'))
            val loadEntered = CompletableDeferred<Unit>()
            val releaseLoad = CompletableDeferred<Unit>()
            fixture.storage.loadHandler = {
                loadEntered.complete(Unit)
                releaseLoad.await()
                ProjectLoadOutcome.Loaded(loaded)
            }
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Retired(generation(1)) }

            val result = async { fixture.workflow.load() }
            loadEntered.await()
            apply(fixture.runtime, position(0, 0), red)
            undo(fixture.runtime)
            apply(fixture.runtime, position(0, 0), green)
            releaseLoad.complete(Unit)

            val confirmation = assertAwaiting(result.await())
            assertEquals(PersistenceConfirmationReason.SOURCE_CHANGED, confirmation.reason)
            assertNotEquals(loaded, fixture.runtime.state.documentState)
            assertCompleted(PersistenceLastOutcome.Loaded, fixture.workflow.confirm(confirmation))
            assertEquals(loaded, fixture.runtime.state.documentState)
        }

    @Test
    fun `an old new-document confirmation is replaced when its source changes`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val first = assertAwaiting(fixture.workflow.createNewDocument(newRequest(3, 2)))
            assertEquals(PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES, first.reason)
            apply(fixture.runtime, position(1, 0), green)

            val refreshed = assertAwaiting(fixture.workflow.confirm(first))
            assertEquals(PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES, refreshed.reason)
            assertNotEquals(first, refreshed)
            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.confirm(first))
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Retired(generation(1)) }
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.confirm(refreshed))
            assertEquals(3, fixture.runtime.state.documentState.size.width.value)
            assertEquals(2, fixture.runtime.state.documentState.size.height.value)
            assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
        }

    @Test
    fun `successful new document uses one retirement and resets every runtime owner`() =
        runBlocking {
            val fixture = initializedFixture()
            val previousViewport =
                ViewportState.create(
                    created(ViewportZoom.create(2.0)),
                    fixture.runtime.state.workspaceState.viewport.center,
                )
            fixture.runtime.reduce(WorkspaceAction.SetViewport(previousViewport))
            fixture.runtime.reduce(WorkspaceAction.SelectTool(DrawingTool.Eraser))
            fixture.runtime.reduce(WorkspaceAction.SelectPaletteEntry(paletteIndex(1)))
            val before = fixture.runtime.state
            fixture.recovery.retireHandler = { expected ->
                assertEquals(ExpectedRecoveryLineage.Missing, expected)
                RecoveryRetirementOutcome.Retired(generation(1))
            }

            assertCompleted(
                PersistenceLastOutcome.NewDocumentCreated,
                fixture.workflow.createNewDocument(newRequest(3, 2)),
            )
            val after = fixture.runtime.state

            assertEquals(2, fixture.ids.callCount)
            assertNotEquals(before.documentState, after.documentState)
            assertEquals(3, after.documentState.size.width.value)
            assertEquals(2, after.documentState.size.height.value)
            assertEquals(HistoryAvailability.None, after.historyAvailability)
            assertEquals(DocumentDirtyState.Clean, after.dirtyState)
            assertEquals(DrawingTool.Pencil, after.workspaceState.activeTool)
            assertEquals(paletteIndex(0), after.workspaceState.activePaletteIndex)
            assertEquals(ViewportState.initial(after.documentState.size), after.workspaceState.viewport)
            assertEquals(listOf(ExpectedRecoveryLineage.Missing), fixture.recovery.retireCalls)
        }

    @Test
    fun `invalid new document preserves owners and consumes no identity`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state

            val result = fixture.workflow.createNewDocument(NewDocumentRequest.create("257", "4"))
            val rejected = assertInstanceOf(PersistenceRequestResult.Rejected::class.java, result)

            assertInstanceOf(NewDocumentRejection.OutsideSupportedRange::class.java, rejected.rejection)
            assertSame(before.documentState, fixture.runtime.state.documentState)
            assertSame(before.workspaceState, fixture.runtime.state.workspaceState)
            assertEquals(1, fixture.ids.callCount)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }

    @Test
    fun `switching rejects mutations and cancellation then preserves old owners on retirement failure`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = prepareDirtyRuntimeWithPreview(fixture)
            val retireEntered = CompletableDeferred<Unit>()
            val releaseRetire = CompletableDeferred<Unit>()
            fixture.recovery.retireHandler = {
                retireEntered.complete(Unit)
                releaseRetire.await()
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.WRITE,
                    RecoveryRollbackOutcome.COMPLETED,
                )
            }

            val confirmation = assertAwaiting(fixture.workflow.createNewDocument(newRequest(3, 2)))
            val result = async { fixture.workflow.confirm(confirmation) }
            retireEntered.await()
            assertEquals(null, fixture.runtime.state.workspaceState.preview)
            assertSwitchingRejectsWork(fixture)
            releaseRetire.complete(Unit)

            val failure = assertFailed(result.await())
            assertInstanceOf(PersistenceFailure.RecoveryRetirementFailed::class.java, failure)
            assertOldOwnersPreserved(fixture, before)
        }

    private fun prepareDirtyRuntimeWithPreview(fixture: Fixture): EditorRuntimeState {
        apply(fixture.runtime, position(0, 0), red)
        val previousViewport =
            ViewportState.create(
                created(ViewportZoom.create(2.0)),
                fixture.runtime.state.workspaceState.viewport.center,
            )
        fixture.runtime.reduce(WorkspaceAction.SetViewport(previousViewport))
        fixture.runtime.reduce(WorkspaceAction.SelectTool(DrawingTool.Eraser))
        fixture.runtime.reduce(WorkspaceAction.SelectPaletteEntry(paletteIndex(1)))
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            fixture.runtime.reduce(
                WorkspaceAction.BeginGesturePreview(
                    fixture.runtime.state.documentState.size,
                    position(1, 0),
                ),
            ),
        )
        val before = fixture.runtime.state
        assertTrue(before.workspaceState.preview != null)
        return before
    }

    private suspend fun assertSwitchingRejectsWork(fixture: Fixture) {
        val handle = assertSwitching(fixture.workflow.operation.value.phase)
        val command = commandFor(fixture.runtime, position(0, 0), red)
        val commandFailure = assertInstanceOf(CommandResult.Failed::class.java, fixture.runtime.execute(command))
        assertEquals(CommandFailure.PersistenceBusy, commandFailure.failure)
        val reduction = fixture.runtime.reduce(WorkspaceAction.SelectTool(DrawingTool.Eraser))
        val rejected = assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, reduction)
        assertEquals(WorkspaceActionRejection.PersistenceBusy, rejected.rejection)
        assertEquals(PersistenceCancellationResult.TooLate, fixture.workflow.cancel(handle))
        assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())
    }

    private fun assertOldOwnersPreserved(
        fixture: Fixture,
        before: EditorRuntimeState,
    ) {
        assertSame(before.documentState, fixture.runtime.state.documentState)
        assertEquals(before.historyAvailability, fixture.runtime.state.historyAvailability)
        assertEquals(before.dirtyState, fixture.runtime.state.dirtyState)
        assertEquals(before.workspaceState.activeTool, fixture.runtime.state.workspaceState.activeTool)
        assertEquals(
            before.workspaceState.activePaletteIndex,
            fixture.runtime.state.workspaceState.activePaletteIndex,
        )
        assertEquals(before.workspaceState.viewport, fixture.runtime.state.workspaceState.viewport)
        assertEquals(null, fixture.runtime.state.workspaceState.preview)
        assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
    }

    @Test
    fun `caller cancellation cannot split retirement success from runtime installation`() =
        runBlocking {
            val fixture = initializedFixture()
            val retireEntered = CompletableDeferred<Unit>()
            val releaseRetire = CompletableDeferred<Unit>()
            fixture.recovery.retireHandler = {
                retireEntered.complete(Unit)
                releaseRetire.await()
                RecoveryRetirementOutcome.Retired(generation(1))
            }

            val result = async { fixture.workflow.createNewDocument(newRequest(3, 2)) }
            retireEntered.await()
            result.cancel()
            releaseRetire.complete(Unit)
            result.join()

            assertEquals(3, fixture.runtime.state.documentState.size.width.value)
            assertEquals(2, fixture.runtime.state.documentState.size.height.value)
            assertEquals(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.operation.value.lastOutcome)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `caller cancellation cannot interrupt verified save cleanup`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }
            val retireEntered = CompletableDeferred<Unit>()
            val releaseRetire = CompletableDeferred<Unit>()
            fixture.recovery.retireHandler = {
                retireEntered.complete(Unit)
                releaseRetire.await()
                RecoveryRetirementOutcome.Retired(generation(1))
            }

            val result = async { fixture.workflow.saveAs() }
            retireEntered.await()
            val handle = assertSaving(fixture.workflow.operation.value.phase)
            assertEquals(PersistenceCancellationResult.TooLate, fixture.workflow.cancel(handle))
            result.cancel()
            releaseRetire.complete(Unit)
            result.join()

            assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
            val saved =
                assertInstanceOf(
                    PersistenceLastOutcome.Saved::class.java,
                    fixture.workflow.operation.value.lastOutcome,
                )
            assertEquals(generation(1), assertRetiredCleanup(saved.recoveryCleanup))
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `stale and uncertain retirement make recovery unknown until a successful reinspection`() =
        runBlocking {
            val outcomes =
                listOf<RecoveryRetirementOutcome>(
                    RecoveryRetirementOutcome.Stale,
                    RecoveryRetirementOutcome.Uncertain(
                        RecoveryRetirementFailure.READ_BACK,
                        RecoveryRollbackOutcome.FAILED,
                    ),
                )
            outcomes.forEachIndexed { index, outcome ->
                val fixture = initializedFixture()
                val oldDocument = fixture.runtime.state.documentState
                fixture.recovery.retireHandler = { outcome }

                assertFailed(fixture.workflow.createNewDocument(newRequest(3, 2)))
                assertSame(oldDocument, fixture.runtime.state.documentState)
                assertInstanceOf(RecoveryStatus.Unknown::class.java, fixture.workflow.operation.value.recoveryStatus)
                assertEquals(PersistenceRequestResult.RecoveryUnavailable, fixture.workflow.load())

                fixture.recovery.inspection = RecoveryInspection.Retired(generation((index + 3).toLong()))
                assertEquals(RecoveryInitializationResult.Ready, fixture.workflow.initializeRecovery())
                assertEquals(RecoveryStatus.Clear, fixture.workflow.operation.value.recoveryStatus)
            }
        }

    @Test
    fun `confirmation cancellation completes immediately and releases the lease`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val confirmation = assertAwaiting(fixture.workflow.load())

            assertEquals(PersistenceCancellationResult.Cancelled, fixture.workflow.cancel(confirmation.operation))
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.confirm(confirmation))

            fixture.storage.saveHandler = { ProjectSaveOutcome.Cancelled }
            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.saveAs())
        }
}
