package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.cellAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class SelectionEditorControllerTest {
    private val surface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE, DENSITY).requiredValue()

    @Test
    fun `release final point retains preview until apply then one undo redo restores exact cells`() {
        val fixture = fixture()
        val controller = fixture.controller
        controller.pointerDown(surface, point(0, 0))
        controller.pointerEnd(surface, point(0, 0))
        val before = controller.documentState
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            controller.callbacks.selection.onBegin(before.layers.single().id),
        )
        drag(controller, point(0, 0), point(0, 0))
        controller.pointerDown(surface, point(0, 0))
        controller.pointerMove(surface, point(1, 0))
        controller.pointerEnd(surface, point(2, 1))
        // ViewportPointerInput also calls cancel from its normal finally after pointer-up.
        controller.pointerCancel()

        val session = requireNotNull(controller.renderState.selectionSession)
        assertEquals(position(2, 1), session.targetRegion?.origin)
        assertEquals(SelectionInteraction.Idle, session.interaction)
        assertTrue(session.canApply)
        assertSame(before, controller.documentState)

        val applied = controller.callbacks.selection.onApply()
        assertNull(applied.selectionSession)
        assertNull(applied.selectionNotice)
        assertEquals(before.revision.value + 1L, applied.document.revision.value)
        assertEquals(PixelCell.Empty, cellAt(applied.document, position(0, 0)))
        assertEquals(cellAt(before, position(0, 0)), cellAt(applied.document, position(2, 1)))
        assertEquals(
            before.layers,
            controller.callbacks
                .onUndo()
                .document.layers,
        )
        assertEquals(
            applied.document.layers,
            controller.callbacks
                .onRedo()
                .document.layers,
        )
    }

    @Test
    fun `cancel restores old draft even while drawing new rectangle then a fresh drag can finish`() {
        val controller = fixture().controller
        val before = controller.documentState
        controller.callbacks.selection.onBegin(before.layers.single().id)
        drag(controller, point(0, 0), point(1, 0))
        drag(controller, point(0, 0), point(0, 1))
        val previous = requireNotNull(controller.renderState.selectionSession).previewTransfer

        controller.pointerDown(surface, point(3, 3))
        controller.pointerMove(surface, point(2, 2))
        controller.pointerCancel()

        assertEquals(previous, controller.renderState.selectionSession?.previewTransfer)
        assertEquals(SelectionInteraction.Idle, controller.renderState.selectionSession?.interaction)
        drag(controller, point(0, 1), point(1, 1))
        assertEquals(
            position(1, 1),
            controller.renderState.selectionSession
                ?.targetRegion
                ?.origin,
        )
        controller.callbacks.selection.onCancel()
        assertNull(controller.renderState.selectionSession)
        assertSame(before, controller.documentState)
        assertFalse(controller.renderState.canUndo)
    }

    @Test
    fun `mode change keeps geometry and the same pencil intent cancels the entire session`() {
        val controller = fixture().controller
        controller.callbacks.selection.onBegin(
            controller.documentState.layers
                .single()
                .id,
        )
        drag(controller, point(0, 0), point(1, 1))
        drag(controller, point(0, 0), point(1, 0))
        val target = controller.renderState.selectionSession?.targetRegion

        controller.callbacks.selection.onMode(PixelTransferMode.Copy)

        assertEquals(target, controller.renderState.selectionSession?.targetRegion)
        assertEquals(PixelTransferMode.Copy, controller.renderState.selectionSession?.mode)
        controller.callbacks.onSelectTool(DrawingTool.Pencil)
        assertNull(controller.renderState.selectionSession)
        assertFalse(controller.renderState.canUndo)
    }

    @Test
    fun `second pointer restores in flight drag while retaining released selection`() {
        val controller = fixture().controller
        controller.callbacks.selection.onBegin(
            controller.documentState.layers
                .single()
                .id,
        )
        drag(controller, point(0, 0), point(1, 1))
        val previous = controller.renderState.selectionSession?.previewTransfer
        controller.pointerDown(surface, point(0, 0))
        controller.pointerMove(surface, point(1, 1))

        controller.viewportStarted(surface)

        assertEquals(previous, controller.renderState.selectionSession?.previewTransfer)
        assertEquals(SelectionInteraction.Idle, controller.renderState.selectionSession?.interaction)
        assertFalse(controller.renderState.canUndo)
    }

    private fun drag(
        controller: EditorController,
        start: ViewportSurfacePoint,
        end: ViewportSurfacePoint,
    ) {
        controller.pointerDown(surface, start)
        controller.pointerEnd(surface, end)
        controller.pointerCancel()
    }

    private fun point(
        x: Int,
        y: Int,
    ): ViewportSurfacePoint =
        ViewportSurfacePoint.create((x + HALF_CELL) * CELL_EDGE, (y + HALF_CELL) * CELL_EDGE).requiredValue()

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Invalid selection viewport fixture: $rejection")
        }

    private companion object {
        const val SURFACE_EDGE = 400
        const val CELL_EDGE = 100.0
        const val HALF_CELL = 0.5
        const val DENSITY = 2.0
    }
}
