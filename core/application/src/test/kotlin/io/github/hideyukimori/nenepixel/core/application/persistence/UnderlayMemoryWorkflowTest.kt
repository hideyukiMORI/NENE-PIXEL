package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR 0034: the workflow writes what the runtime asks for and applies what the port recalls. */
internal class UnderlayMemoryWorkflowTest {
    @Test
    fun `a placed underlay is remembered for its work and the memory settles`() {
        runBlocking {
            val fixture = initializedFixture()
            val underlay = memoryUnderlay()
            placeUnderlay(fixture, underlay)

            fixture.workflow.underlayMemory.publish()

            assertEquals(remembered(underlay), fixture.underlayMemory.stored(documentOf(fixture)))
            assertEquals(UnderlayMemoryProjection.Settled, fixture.workflow.underlayMemory.states.value)
        }
    }

    @Test
    fun `a moved underlay replaces the remembered placement with the same image`() {
        runBlocking {
            val placed = memoryUnderlay()
            val fixture = publishedFixture(placed)
            val moved = placed.withPlacement(-2.0, 0.0, 1.0)
            placeUnderlay(fixture, moved)

            fixture.workflow.underlayMemory.publish()

            val stored = fixture.underlayMemory.stored(documentOf(fixture))
            assertEquals(remembered(moved), stored)
            assertSame(placed.image, stored?.image)
            assertSettled(fixture)
        }
    }

    @Test
    fun `a removed underlay forgets the record of its work`() {
        runBlocking {
            val fixture = publishedFixture()
            fixture.runtime.reduce(WorkspaceAction.ClearReferenceUnderlay)

            fixture.workflow.underlayMemory.publish()

            val document = documentOf(fixture)
            assertEquals(FakeUnderlayMemoryCall.Forget(document), fixture.underlayMemory.calls.last())
            assertNull(fixture.underlayMemory.stored(document))
            assertSettled(fixture)
        }
    }

    @Test
    fun `loading a remembered work restores its underlay and leaves the document alone`() {
        runBlocking {
            val writer = initializedFixture()
            val underlay =
                memoryUnderlay()
                    .withPlacement(-2.0, 0.0, 1.0)
                    .withOpacity(UnderlayOpacity.create(OPACITY))
                    .toggledVisibility()
            placeUnderlay(writer, underlay)
            writer.workflow.underlayMemory.publish()
            val reader = Fixture(underlayMemory = writer.underlayMemory).also { it.initialize() }
            loadWork(reader, documentOf(writer))
            val before = reader.runtime.state

            reader.workflow.underlayMemory.recall()

            val after = reader.runtime.state
            assertEquals(remembered(underlay), remembered(after.workspaceState.underlay))
            assertSettled(reader)
            assertSame(before.documentState, after.documentState)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(before.historyAvailability, after.historyAvailability)
        }
    }

    @Test
    fun `a work with nothing remembered stays empty and forgets nothing`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)

            fixture.workflow.underlayMemory.recall()

            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertSettled(fixture)
            val calls = fixture.underlayMemory.calls
            assertEquals(listOf(FakeUnderlayMemoryCall.Recall(documentOf(fixture))), calls)
        }
    }

    @Test
    fun `flush writes an underlay being adjusted and publish does not`() {
        runBlocking {
            val fixture = publishedFixture()
            val adjusting = memoryUnderlay().adjusting().withPlacement(-2.0, 0.0, 1.0)
            placeUnderlay(fixture, adjusting)
            val callsBefore = fixture.underlayMemory.calls

            fixture.workflow.underlayMemory.publish()
            assertEquals(callsBefore, fixture.underlayMemory.calls)

            fixture.workflow.underlayMemory.flush()
            assertEquals(remembered(adjusting), fixture.underlayMemory.stored(documentOf(fixture)))
            val flushed = FakeUnderlayMemoryCall.Remember(documentOf(fixture))
            assertEquals(callsBefore + flushed, fixture.underlayMemory.calls)
        }
    }

    @Test
    fun `nothing to recall or write calls no port`() {
        runBlocking {
            val fixture = initializedFixture()
            val memory = fixture.workflow.underlayMemory

            memory.recall()
            memory.publish()
            memory.flush()

            assertTrue(fixture.underlayMemory.calls.isEmpty())
            assertSettled(fixture)
        }
    }

    private suspend fun publishedFixture(underlay: ReferenceUnderlay = memoryUnderlay()): Fixture {
        val fixture = initializedFixture()
        placeUnderlay(fixture, underlay)
        fixture.workflow.underlayMemory.publish()
        assertSettled(fixture)
        return fixture
    }

    private companion object {
        const val OPACITY = 200
    }
}

internal fun documentOf(fixture: Fixture): DocumentId = fixture.runtime.state.documentState.id

/** Loads a 4 x 4 work with [document] as its id through the project storage fake. */
internal suspend fun loadWork(
    fixture: Fixture,
    document: DocumentId,
) {
    val loaded = state(canvas(4, 4), documentId = document)
    fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(loaded)) }
    fixture.recovery.retireHandler = { expected -> RecoveryRetirementOutcome.Retired(nextGeneration(expected)) }
    assertCompleted(PersistenceLastOutcome.Loaded, fixture.workflow.load())
}
