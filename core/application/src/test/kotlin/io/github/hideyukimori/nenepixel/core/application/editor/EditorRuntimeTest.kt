package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class EditorRuntimeTest {
    @Test
    fun `initial runtime uses one canonical blank clean empty-history construction`() {
        val canvas = canvas(2, 3)
        val ids = SequentialDocumentIdSource()

        val state = EditorRuntime.create(canvas, red, ids).state

        assertEquals(1, ids.callCount)
        assertEquals(ids.first, state.documentState.id)
        assertEquals(canvas, state.documentState.size)
        assertEquals(0L, state.documentState.revision.value)
        assertEquals(
            List(6) {
                PixelColor.blank.toPackedRgba8888()
            },
            state.documentState.snapshot
                .copyPackedRgba8888()
                .toList(),
        )
        assertEquals(HistoryAvailability.None, state.historyAvailability)
        assertEquals(DocumentDirtyState.Clean, state.dirtyState)
        assertEquals(red, state.workspaceState.activeColor)
        assertEquals(ViewportState.initial(canvas), state.workspaceState.viewport)
        assertNull(state.workspaceState.preview)
    }

    @Test
    fun `successful creation atomically resets document history dirty workspace and viewport`() {
        val ids = SequentialDocumentIdSource()
        val runtime = EditorRuntime.create(canvas(4, 4), red, ids)
        applyOnePixel(runtime)
        val previousViewport =
            ViewportState.create(
                created(ViewportZoom.create(2.0)),
                runtime.state.workspaceState.viewport.center,
            )
        runtime.reduce(WorkspaceAction.SetViewport(previousViewport))
        val before = runtime.state

        val result = runtime.createNewDocument(NewDocumentRequest.create("3", "2"))
        val created = assertInstanceOf(NewDocumentResult.Created::class.java, result)
        val after = runtime.state

        assertEquals(2, ids.callCount)
        assertEquals(ids.second, after.documentState.id)
        assertEquals(3, after.documentState.size.width.value)
        assertEquals(2, after.documentState.size.height.value)
        assertEquals(0L, after.documentState.revision.value)
        assertEquals(
            List(6) {
                PixelColor.blank.toPackedRgba8888()
            },
            after.documentState.snapshot
                .copyPackedRgba8888()
                .toList(),
        )
        assertEquals(HistoryAvailability.None, after.historyAvailability)
        assertEquals(DocumentDirtyState.Clean, after.dirtyState)
        assertEquals(ViewportState.initial(after.documentState.size), after.workspaceState.viewport)
        assertNull(after.workspaceState.preview)
        assertNotEquals(before.documentState, after.documentState)
        assertNotEquals(previousViewport, after.workspaceState.viewport)
        assertEquals(after, created.state)
    }

    @Test
    fun `rejected request invokes no identity or construction and preserves owner state`() {
        val ids = SequentialDocumentIdSource()
        val runtime = EditorRuntime.create(canvas(4, 4), red, ids)
        val changedViewport =
            ViewportState.create(
                created(ViewportZoom.create(2.0)),
                runtime.state.workspaceState.viewport.center,
            )
        runtime.reduce(WorkspaceAction.SetViewport(changedViewport))
        val before = runtime.state

        val result = runtime.createNewDocument(NewDocumentRequest.create("257", "4"))
        val rejected = assertInstanceOf(NewDocumentResult.Rejected::class.java, result)
        val after = runtime.state

        assertEquals(1, ids.callCount)
        assertSame(before.documentState, after.documentState)
        assertSame(before.workspaceState, after.workspaceState)
        assertEquals(before.historyAvailability, after.historyAvailability)
        assertEquals(before.dirtyState, after.dirtyState)
        assertEquals(before, rejected.state)
        assertInstanceOf(NewDocumentRejection.OutsideSupportedRange::class.java, rejected.rejection)
    }

    @Test
    fun `only an applied document command marks runtime dirty`() {
        val runtime = EditorRuntime.create(canvas(2, 2), red, SequentialDocumentIdSource())
        val initial = runtime.state
        val wrongTarget = SequentialDocumentIdSource().second
        val stroke = stroke(initial.documentState.size, listOf(position(0, 0)), red)

        val rejected =
            runtime.execute(
                ApplyStrokeCommand.create(wrongTarget, initial.documentState.revision, stroke),
            )
        assertInstanceOf(CommandResult.Rejected::class.java, rejected)
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)

        applyOnePixel(runtime)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
    }

    private fun applyOnePixel(runtime: EditorRuntime) {
        val target = runtime.state.documentState
        val result =
            runtime.execute(
                ApplyStrokeCommand.create(
                    target.id,
                    target.revision,
                    stroke(target.size, listOf(position(0, 0)), red),
                ),
            )
        assertInstanceOf(CommandResult.Applied::class.java, result)
    }

    private fun created(result: ViewportValueResult<ViewportZoom>): ViewportZoom =
        when (result) {
            is ViewportValueResult.Created -> result.value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: ${result.rejection}")
        }
}

private class SequentialDocumentIdSource : DocumentIdSource {
    val first: DocumentId = documentId('1')
    val second: DocumentId = documentId('2')
    var callCount: Int = 0
        private set

    override fun nextDocumentId(): DocumentId {
        val next =
            when (callCount) {
                0 -> first
                1 -> second
                else -> documentId('3')
            }
        callCount += 1
        return next
    }
}

private fun documentId(character: Char): DocumentId =
    when (val result = DocumentId.create(character.toString().repeat(32))) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> fail("Document ID fixture was rejected: ${result.rejection}")
    }
