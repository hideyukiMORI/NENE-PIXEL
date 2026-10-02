package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryWrite
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublicationMode
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublicationStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** ADR 0034: what the runtime asks to publish, and what the memory operations leave alone. */
internal class UnderlayMemoryRuntimePublicationTest {
    @Test
    fun `an adjusting underlay stays settled until it rests`() {
        runBlocking {
            val fixture = publishedFixture()
            val adjusting = memoryUnderlay(width = 6).adjusting()
            placeUnderlay(fixture, adjusting)
            assertSettled(fixture)
            val start = fixture.runtime.underlayMemoryOperations.beginPublication(UnderlayPublicationMode.Publish)
            assertEquals(UnderlayPublicationStart.NotNeeded, start)

            placeUnderlay(fixture, adjusting.rested())
            assertPublishPending(fixture)
        }
    }

    @Test
    fun `a flush writes the value being adjusted`() {
        runBlocking {
            val fixture = publishedFixture()
            val adjusting = memoryUnderlay(width = 6).adjusting()
            placeUnderlay(fixture, adjusting)

            val publication = beginPublication(fixture, UnderlayPublicationMode.Flush)

            val document = fixture.runtime.state.documentState.id
            assertEquals(listOf(UnderlayMemoryWrite.of(document, remembered(adjusting))), publication.writes)
        }
    }

    @Test
    fun `an unpublished value of the departing work is written first and alone`() {
        runBlocking {
            val fixture = initializedFixture()
            val departingDocument = fixture.runtime.state.documentState.id
            val underlay = memoryUnderlay()
            placeUnderlay(fixture, underlay)
            installNewDocument(fixture)
            assertPublishPending(fixture)

            val publication = beginPublication(fixture)
            assertEquals(listOf(UnderlayMemoryWrite.of(departingDocument, remembered(underlay))), publication.writes)
            fixture.runtime.underlayMemoryOperations.completePublication(publication)

            assertRecallPending(fixture)
            assertEquals(fixture.runtime.state.documentState.id, beginRecall(fixture).document)
        }
    }

    @Test
    fun `drawing, viewport and tool reductions publish no new memory value`() {
        runBlocking {
            val fixture = initializedFixture()
            placeUnderlay(fixture, memoryUnderlay())
            val values = mutableListOf<UnderlayMemoryProjection>()
            val collector = launch(Dispatchers.Unconfined) { fixture.runtime.underlayMemory.collect { values += it } }

            drawAndNavigate(fixture)
            assertEquals(1, values.size)
            placeUnderlay(fixture, memoryUnderlay(width = 6))
            assertEquals(2, values.size)
            collector.cancel()
        }
    }

    @Test
    fun `memory operations leave the persistence operation and its lease alone`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }
            val idle = fixture.runtime.persistenceOperation.value

            val recall = beginRecall(fixture)
            assertEquals(idle, fixture.runtime.persistenceOperation.value)
            assertSaved(fixture.workflow.saveAs())
            val saved = fixture.runtime.persistenceOperation.value
            fixture.runtime.underlayMemoryOperations.completeRecall(recall, UnderlayRecollection.Absent)
            assertEquals(saved, fixture.runtime.persistenceOperation.value)
            placeUnderlay(fixture, memoryUnderlay())

            val publication = beginPublication(fixture)
            assertSaved(fixture.workflow.saveAs())
            val savedAgain = fixture.runtime.persistenceOperation.value
            fixture.runtime.underlayMemoryOperations.completePublication(publication)
            assertEquals(savedAgain, fixture.runtime.persistenceOperation.value)
            assertEquals(PersistenceOperationPhase.Idle, savedAgain.phase)
            assertSettled(fixture)
        }
    }

    @Test
    fun `a restored underlay keeps the gesture preview`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val start = beginRecall(fixture)
            val size = fixture.runtime.state.documentState.size
            val preview = fixture.runtime.reduce(WorkspaceAction.BeginGesturePreview(size, position(1, 0)))
            assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, preview)

            fixture.runtime.underlayMemoryOperations.completeRecall(start, recollectionOf(memoryUnderlay()))

            assertNotNull(fixture.runtime.state.workspaceState.underlay)
            assertNotNull(fixture.runtime.state.workspaceState.preview)
            assertSettled(fixture)
        }
    }

    @Test
    fun `a restored underlay is reduced during a palette session`() {
        runBlocking {
            val fixture = initializedFixture()
            installNewDocument(fixture)
            val start = beginRecall(fixture)
            val session = fixture.runtime.paletteOperations.beginPaletteEdit()
            assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, session)

            fixture.runtime.underlayMemoryOperations.completeRecall(start, recollectionOf(memoryUnderlay()))

            assertNotNull(fixture.runtime.state.workspaceState.underlay)
            assertNotNull(fixture.runtime.state.workspaceState.paletteEditSession)
            assertSettled(fixture)
        }
    }

    /** A fixture whose installed work has a published, resting underlay. */
    private suspend fun publishedFixture(): Fixture {
        val fixture = initializedFixture()
        placeUnderlay(fixture, memoryUnderlay())
        fixture.runtime.underlayMemoryOperations.completePublication(beginPublication(fixture))
        assertSettled(fixture)
        return fixture
    }

    private fun drawAndNavigate(fixture: Fixture) {
        val runtime = fixture.runtime
        val viewport =
            ViewportState.create(created(ViewportZoom.create(2.0)), runtime.state.workspaceState.viewport.center)
        runtime.reduce(WorkspaceAction.SetViewport(viewport))
        runtime.reduce(WorkspaceAction.SelectTool(DrawingTool.Eraser))
        runtime.reduce(WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position(1, 0)))
        apply(runtime, position(0, 0), red)
    }
}
