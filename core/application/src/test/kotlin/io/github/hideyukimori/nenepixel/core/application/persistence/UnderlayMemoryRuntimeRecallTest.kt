package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.DocumentIdentity
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryWrite
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayRecallStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** ADR 0034: the runtime starts settled, every installation recalls, and a recall restores or keeps. */
internal class UnderlayMemoryRuntimeRecallTest {
    @Test
    fun `startup is settled and a placed underlay is published until its completion`() {
        runBlocking {
            val fixture = initializedFixture()
            assertSettled(fixture)
            assertEquals(UnderlayRecallStart.NotNeeded, fixture.runtime.underlayMemoryOperations.beginRecall())
            val underlay = memoryUnderlay()
            placeUnderlay(fixture, underlay)
            assertPublishPending(fixture)

            val publication = beginPublication(fixture)
            val document = fixture.runtime.state.documentState.id
            assertEquals(listOf(UnderlayMemoryWrite.of(document, remembered(underlay))), publication.writes)
            fixture.runtime.underlayMemoryOperations.completePublication(publication)
            assertSettled(fixture)
        }
    }

    @Test
    fun `a new document recalls`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            assertEquals(fixture.runtime.state.documentState.id, beginRecall(fixture).document)
        }
    }

    @Test
    fun `a loaded document recalls`() {
        runBlocking {
            val fixture = initializedFixture()
            val loaded = state(canvas(4, 4), identity = DocumentIdentity(documentId('b')))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(loaded)) }
            fixture.recovery.retireHandler = { RecoveryRetirementOutcome.Retired(generation(1)) }
            assertCompleted(PersistenceLastOutcome.Loaded, fixture.workflow.load())
            assertRecallPending(fixture)
            assertEquals(loaded.id, beginRecall(fixture).document)
        }
    }

    @Test
    fun `an accepted recovery recalls`() {
        runBlocking {
            val candidate = state(canvas(4, 4), identity = DocumentIdentity(documentId('c')))
            val fixture =
                Fixture(RecoveryInspection.Candidate(generation(9), DocumentImportSource.Current(candidate)))
            fixture.initialize()
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.acceptRecovery())
            assertRecallPending(fixture)
            assertEquals(candidate.id, beginRecall(fixture).document)
        }
    }

    @Test
    fun `a PNG opened as a new work recalls`() {
        runBlocking {
            val fixture = initializedFixture()
            setPendingNewWork(fixture)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.pngImport.openAsNewWork())
            assertRecallPending(fixture)
            assertEquals(fixture.runtime.state.documentState.id, beginRecall(fixture).document)
        }
    }

    @Test
    fun `a remembered value of an untouched work is restored and leaves everything else alone`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            apply(fixture.runtime, position(0, 0), red)
            val before = fixture.runtime.state
            val operation = fixture.workflow.operation.value
            val underlay = memoryUnderlay()

            fixture.runtime.underlayMemoryOperations.completeRecall(beginRecall(fixture), recollectionOf(underlay))

            val after = fixture.runtime.state
            assertEquals(remembered(underlay), remembered(after.workspaceState.underlay))
            assertSettled(fixture)
            assertSame(before.documentState, after.documentState)
            assertEquals(before.historyAvailability, after.historyAvailability)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(operation, fixture.workflow.operation.value)
        }
    }

    @Test
    fun `an underlay chosen before the recall is kept and published`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val start = beginRecall(fixture)
            val chosen = memoryUnderlay(width = 6)
            placeUnderlay(fixture, chosen)

            fixture.runtime.underlayMemoryOperations.completeRecall(start, recollectionOf(memoryUnderlay()))

            assertSame(chosen, fixture.runtime.state.workspaceState.underlay)
            assertPublishPending(fixture)
            val document = fixture.runtime.state.documentState.id
            assertEquals(listOf(UnderlayMemoryWrite.of(document, remembered(chosen))), beginPublication(fixture).writes)
        }
    }

    @Test
    fun `an underlay chosen and removed before the recall forgets the remembered record`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val start = beginRecall(fixture)
            placeUnderlay(fixture, memoryUnderlay(width = 6))
            fixture.runtime.reduce(WorkspaceAction.ClearReferenceUnderlay)

            fixture.runtime.underlayMemoryOperations.completeRecall(start, recollectionOf(memoryUnderlay()))

            assertNull(fixture.runtime.state.workspaceState.underlay)
            val document = fixture.runtime.state.documentState.id
            assertEquals(listOf(UnderlayMemoryWrite.Forget(document)), beginPublication(fixture).writes)
        }
    }

    @Test
    fun `a recall of another installation changes nothing`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val stale = beginRecall(fixture)
            installNewDocument(fixture)
            val projection = fixture.runtime.underlayMemory.value

            fixture.runtime.underlayMemoryOperations.completeRecall(stale, recollectionOf(memoryUnderlay()))

            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertEquals(projection, fixture.runtime.underlayMemory.value)
            assertEquals(stale.installation + 1, beginRecall(fixture).installation)
        }
    }

    @Test
    fun `a recall completed while a switch installs another work changes nothing`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val start = beginRecall(fixture)
            val projection = fixture.runtime.underlayMemory.value
            val retireEntered = CompletableDeferred<Unit>()
            val releaseRetire = CompletableDeferred<Unit>()
            fixture.recovery.retireHandler = { expected ->
                retireEntered.complete(Unit)
                releaseRetire.await()
                RecoveryRetirementOutcome.Retired(nextGeneration(expected))
            }
            val switch = async { fixture.workflow.createNewDocument(newRequest(4, 4)) }
            retireEntered.await()
            assertSwitching(fixture.workflow.operation.value.phase)

            fixture.runtime.underlayMemoryOperations.completeRecall(start, recollectionOf(memoryUnderlay()))

            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertEquals(projection, fixture.runtime.underlayMemory.value)
            assertEquals(start, beginRecall(fixture))
            releaseRetire.complete(Unit)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, switch.await())
            assertEquals(start.installation + 1, beginRecall(fixture).installation)
        }
    }
}
