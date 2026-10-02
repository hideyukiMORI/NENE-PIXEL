package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** ADR 0034: failures, slow recalls and reloads, and what the workflow leaves to persistence. */
internal class UnderlayMemoryWorkflowOrderingTest {
    @Test
    fun `a failed value settles, is not asked again, and a new value is asked`() {
        runBlocking {
            val fixture = initializedFixture()
            val underlay = memoryUnderlay()
            placeUnderlay(fixture, underlay)
            fixture.underlayMemory.failNextWrite = true

            fixture.workflow.underlayMemory.publish()
            assertSettled(fixture)
            val afterFailure = fixture.underlayMemory.calls
            fixture.workflow.underlayMemory.publish()
            assertEquals(afterFailure, fixture.underlayMemory.calls)

            val changed = underlay.withOpacity(UnderlayOpacity.create(OPACITY))
            placeUnderlay(fixture, changed)
            fixture.workflow.underlayMemory.publish()
            assertEquals(remembered(changed), fixture.underlayMemory.stored(documentOf(fixture)))
            assertEquals(afterFailure.size + 1, fixture.underlayMemory.calls.size)
        }
    }

    @Test
    fun `an underlay chosen during a slow recall stays and is the one written`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val document = documentOf(fixture)
            fixture.underlayMemory.seed(document, requireNotNull(remembered(memoryUnderlay(width = 6))))
            val gate = fixture.underlayMemory.holdNextRecall()
            val recall = async { fixture.workflow.underlayMemory.recall() }
            gate.awaitStarted()
            val chosen = memoryUnderlay()
            placeUnderlay(fixture, chosen)

            gate.release()
            recall.await()
            assertSame(chosen, fixture.runtime.state.workspaceState.underlay)

            fixture.workflow.underlayMemory.publish()
            assertEquals(remembered(chosen), fixture.underlayMemory.stored(document))
            assertSettled(fixture)
        }
    }

    @Test
    fun `persistence neither waits for the memory nor sees its recall, publication or failure`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }
            val gate = fixture.underlayMemory.holdNextRecall()
            val recall = async { fixture.workflow.underlayMemory.recall() }
            gate.awaitStarted()

            assertSaved(fixture.workflow.saveAs())
            val saved = fixture.workflow.operation.value
            gate.release()
            recall.await()
            assertEquals(saved, fixture.workflow.operation.value)

            placeUnderlay(fixture, memoryUnderlay())
            fixture.underlayMemory.failNextWrite = true
            fixture.workflow.underlayMemory.publish()
            assertEquals(saved, fixture.workflow.operation.value)
            assertEquals(PersistenceOperationPhase.Idle, saved.phase)
            assertSettled(fixture)
        }
    }

    @Test
    fun `reloading a work writes its unpublished value first and recalls that value`() {
        runBlocking {
            val fixture = initializedFixture()
            val document = documentOf(fixture)
            val placed = memoryUnderlay()
            placeUnderlay(fixture, placed)
            fixture.workflow.underlayMemory.publish()
            val moved = placed.withPlacement(-2.0, 0.0, 1.0)
            placeUnderlay(fixture, moved)
            loadWork(fixture, document)

            fixture.workflow.underlayMemory.publish()
            assertEquals(remembered(moved), fixture.underlayMemory.stored(document))
            fixture.workflow.underlayMemory.recall()

            assertEquals(remembered(moved), remembered(fixture.runtime.state.workspaceState.underlay))
            assertSettled(fixture)
        }
    }

    private companion object {
        const val OPACITY = 200
    }
}
