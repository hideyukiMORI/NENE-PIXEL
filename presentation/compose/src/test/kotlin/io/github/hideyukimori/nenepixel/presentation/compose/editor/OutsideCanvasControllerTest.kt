package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportCenter
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.cellAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class OutsideCanvasControllerTest {
    private val surface = ViewportSurface.create(400, 400, 2.0).value()

    @Test
    fun `exit and return keeps samples and commits only the crossed canvas cells with one undo`() {
        val editor = fixture()
        editor.controller.pointerDown(surface, point(50.0, 50.0))
        val outside = editor.controller.pointerMove(surface, point(-150.0, 50.0))
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, outside)
        assertEquals(3, requireNotNull(outside.renderState.preview).positionCount)
        assertSame(editor.initialDocument, outside.renderState.document)
        editor.controller.pointerMove(surface, point(-150.0, 250.0))
        editor.controller.pointerMove(surface, point(250.0, 250.0))
        val end = editor.controller.pointerEnd(surface, point(350.0, 250.0))
        assertInstanceOf(CommandResult.Applied::class.java, accepted(end).commandResult)
        assertNull(end.renderState.preview)
        assertEquals(1L, end.renderState.document.revision.value)
        repeat(4) { y ->
            repeat(4) { x ->
                val expected =
                    if ((y == 0 && x == 0) ||
                        y == 2
                    ) {
                        PixelCell.Covered(editor.initialWorkspace.activePaletteIndex)
                    } else {
                        PixelCell.Empty
                    }
                assertEquals(expected, cellAt(end.renderState.document, position(x, y)), "cell ($x, $y)")
            }
        }
        val undone = editor.controller.callbacks.onUndo()
        assertEquals(editor.initialDocument, undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun `outside start stays ignored even after returning inside`() {
        val editor = fixture()
        assertInstanceOf(
            PointerInputAcknowledgement.Ignored::class.java,
            editor.controller.pointerDown(surface, point(-50.0, 50.0)),
        )
        editor.controller.pointerMove(surface, point(50.0, 50.0))
        editor.controller.pointerEnd(surface, point(250.0, 50.0))
        assertSame(editor.initialDocument, editor.controller.documentState)
        assertNull(editor.controller.workspaceState.preview)
        assertFalse(editor.controller.renderState.canUndo)
    }

    @Test
    fun `system cancel after exiting discards the entire preview`() {
        val editor = fixture()
        editor.controller.pointerDown(surface, point(50.0, 50.0))
        editor.controller.pointerMove(surface, point(450.0, 50.0))
        assertInstanceOf(PointerInputAcknowledgement.Cancelled::class.java, editor.controller.pointerCancel())
        assertNull(editor.controller.workspaceState.preview)
        assertSame(editor.initialDocument, editor.controller.documentState)
        assertFalse(editor.controller.renderState.canUndo)
    }

    @Test
    fun `eraser outside release clears only its contained cells and one undo restores them`() {
        val editor = fixture()
        editor.controller.pointerDown(surface, point(50.0, 50.0))
        editor.controller.pointerEnd(surface, point(350.0, 50.0))
        val painted = editor.controller.documentState
        editor.controller.callbacks.onSelectTool(DrawingTool.Eraser)
        editor.controller.pointerDown(surface, point(150.0, 50.0))
        val end = editor.controller.pointerEnd(surface, point(550.0, 50.0))
        assertInstanceOf(CommandResult.Applied::class.java, accepted(end).commandResult)
        assertEquals(2L, end.renderState.document.revision.value)
        assertEquals(cellAt(painted, position(0, 0)), cellAt(end.renderState.document, position(0, 0)))
        (1..3).forEach { x -> assertEquals(PixelCell.Empty, cellAt(end.renderState.document, position(x, 0))) }
        assertEquals(
            painted,
            editor.controller.callbacks
                .onUndo()
                .document,
        )
    }

    @Test
    fun `move and end cancel when surface normalization changes the viewport`() {
        listOf(false, true).forEach { ending ->
            val editor = fixture()
            val viewport =
                ViewportState.create(
                    ViewportZoom.create(2.0).value(),
                    ViewportCenter.create(1.0, 1.0).value(),
                )
            editor.runtime.reduce(WorkspaceAction.SetViewport(viewport))
            editor.controller.pointerDown(surface, point(50.0, 50.0))
            val enlarged = ViewportSurface.create(800, 400, 2.0).value()
            val result =
                if (ending) {
                    editor.controller.pointerEnd(
                        enlarged,
                        point(850.0, 50.0),
                    )
                } else {
                    editor.controller.pointerMove(enlarged, point(850.0, 50.0))
                }
            assertInstanceOf(PointerInputAcknowledgement.Cancelled::class.java, result)
            assertNull(result.renderState.preview)
            assertSame(editor.initialDocument, result.renderState.document)
        }
    }

    private fun point(
        x: Double,
        y: Double,
    ): ViewportSurfacePoint = ViewportSurfacePoint.create(x, y).value()

    private fun accepted(result: PointerInputAcknowledgement): PointerInputAcknowledgement.Accepted =
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, result)

    private fun <T> ViewportValueResult<T>.value(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> error("Invalid viewport fixture: $rejection")
        }
}
