package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.cellAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SingleFrameTestValues.layerOf
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SingleFrameTestValues.layersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class SelectionTransformControllerTest {
    private val surface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE, DENSITY).value()

    @Test
    fun `flip then rotation remains an in place candidate and translation applies one exact history step`() {
        val controller = fixture().controller
        drag(controller, surface, point(0, 0), point(0, 0))
        val before = controller.documentState
        controller.callbacks.selection.onBegin(
            layerOf(before).id,
        )
        drag(controller, surface, point(0, 0), point(1, 0))

        controller.callbacks.selection.onTransform(PixelOrientation.FlipHorizontal)
        val rotated = controller.callbacks.selection.onTransform(PixelOrientation.RotateClockwise)
        val session = requireNotNull(rotated.selectionSession)
        assertEquals(PixelOrientation.Transverse, session.previewTransfer?.orientation)
        assertEquals(canvas(1, 2), session.targetRegion?.size)
        assertTrue(session.canApply)
        assertSame(before, controller.documentState)
        drag(controller, surface, point(0, 0), point(1, 1))
        val applied = controller.callbacks.selection.onApply()

        assertNull(applied.selectionSession)
        assertNull(applied.selectionNotice)
        assertEquals(before.revision.value + 1L, applied.document.revision.value)
        assertEquals(PixelCell.Empty, cellAt(applied.document, position(0, 0)))
        assertEquals(cellAt(before, position(0, 0)), cellAt(applied.document, position(1, 2)))
        assertEquals(
            layersOf(before),
            layersOf(controller.callbacks.onUndo().document),
        )
        assertEquals(
            layersOf(applied.document),
            layersOf(controller.callbacks.onRedo().document),
        )
    }

    @Test
    fun `unfinished pointer refusal is published without cancelling and cancel restores orientation`() {
        val controller = fixture().controller
        controller.callbacks.selection.onBegin(
            controller.documentState.frames
                .single()
                .layers
                .single()
                .id,
        )
        drag(controller, surface, point(0, 0), point(1, 0))
        controller.callbacks.selection.onTransform(PixelOrientation.RotateClockwise)
        val previous = controller.renderState.selectionSession?.previewTransfer
        controller.pointerDown(surface, point(0, 0))
        controller.pointerMove(surface, point(1, 1))
        val active = controller.renderState.selectionSession

        val refused = controller.callbacks.selection.onTransform(PixelOrientation.FlipVertical)

        assertSame(active, refused.selectionSession)
        assertRejected(SelectionRejection.PointerActive, refused.selectionNotice)
        controller.pointerCancel()
        assertEquals(previous, controller.renderState.selectionSession?.previewTransfer)
        controller.callbacks.selection.onTransform(PixelOrientation.FlipVertical)
        assertNull(controller.renderState.selectionNotice)
        assertFalse(controller.renderState.canUndo)
    }

    @Test
    fun `impossible rotation keeps draft and publishes the localized bounds reason`() {
        val controller = fixture(canvas(4, 2)).controller
        val wideSurface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE / 2, DENSITY).value()
        val before = controller.documentState
        controller.callbacks.selection.onBegin(
            before.frames
                .single()
                .layers
                .single()
                .id,
        )
        drag(controller, wideSurface, point(0, 0), point(2, 0))
        val session = requireNotNull(controller.renderState.selectionSession)
        assertFalse(session.canTransform(PixelOrientation.RotateClockwise))

        val refused = controller.callbacks.selection.onTransform(PixelOrientation.RotateClockwise)

        assertSame(session, refused.selectionSession)
        assertSame(before, refused.document)
        assertRejected(SelectionRejection.TransformedRegionOutsideCanvas, refused.selectionNotice)
        assertEquals(R.string.selection_rotation_outside_canvas, requireNotNull(refused.selectionNotice).resource())
        controller.callbacks.selection.onTransform(PixelOrientation.FlipHorizontal)
        assertNull(controller.renderState.selectionNotice)
    }

    private fun assertRejected(
        reason: SelectionRejection,
        notice: SelectionEditorNotice?,
    ) {
        val rejected = assertInstanceOf(SelectionEditorNotice.Rejected::class.java, notice)
        val action = assertInstanceOf(WorkspaceActionRejection.SelectionRejected::class.java, rejected.rejection)
        assertEquals(reason, action.reason)
    }

    private fun drag(
        controller: EditorController,
        surface: ViewportSurface,
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
        ViewportSurfacePoint.create((x + HALF_CELL) * CELL_EDGE, (y + HALF_CELL) * CELL_EDGE).value()

    private fun <T> ViewportValueResult<T>.value(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Invalid transform controller fixture: $rejection")
        }

    private companion object {
        const val SURFACE_EDGE = 400
        const val CELL_EDGE = 100.0
        const val HALF_CELL = 0.5
        const val DENSITY = 2.0
    }
}
