package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.begin
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.move
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.reduce
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.select
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.session
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SelectionTransformTest {
    @Test
    fun `non-square rotation anchors then clamps and translation uses oriented dimensions`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime, position(5, 4), DocumentPoint.create(7, 5))
        val committed = runtime.state.documentState
        transform(runtime, PixelOrientation.RotateClockwise)
        val rotated = session(runtime)
        assertEquals(canvas(2, 3), rotated.targetRegion?.size)
        assertEquals(position(5, 3), rotated.targetRegion?.origin)
        assertTrue(rotated.canApply)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(5, 3)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(Int.MAX_VALUE, Int.MIN_VALUE)))
        assertEquals(position(6, 0), session(runtime).targetRegion?.origin)
        assertFalse(session(runtime).canTransform(PixelOrientation.FlipHorizontal))
        reduce(runtime, WorkspaceAction.CancelSelectionPointer)
        assertEquals(rotated.previewTransfer, session(runtime).previewTransfer)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(5, 3)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(0, 0)))
        reduce(runtime, WorkspaceAction.SetSelectionMode(PixelTransferMode.Copy))
        assertEquals(PixelOrientation.RotateClockwise, session(runtime).previewTransfer?.orientation)
        assertEquals(rotated.targetRegion, session(runtime).targetRegion)
        repeat(3) { transform(runtime, PixelOrientation.RotateClockwise) }
        assertEquals(PixelOrientation.Identity, session(runtime).previewTransfer?.orientation)
        assertEquals(position(5, 3), session(runtime).targetRegion?.origin)
        assertTrue(session(runtime).canApply)
        assertSame(committed, runtime.state.documentState)
    }

    @Test
    fun `impossible rotated dimensions reject while active pointer takes precedence atomically`() {
        val runtime = runtime(canvas(8, 2))
        begin(runtime)
        select(runtime, position(0, 0), DocumentPoint.create(2, 1))
        val retained = runtime.state
        assertFalse(session(runtime).canTransform(PixelOrientation.RotateClockwise))
        assertTrue(session(runtime).canTransform(PixelOrientation.FlipHorizontal))
        assertRejected(runtime, SelectionRejection.TransformedRegionOutsideCanvas)
        assertEquals(retained, runtime.state)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(0, 0)))
        val active = runtime.state
        assertRejected(runtime, SelectionRejection.PointerActive)
        assertEquals(active, runtime.state)
        reduce(runtime, WorkspaceAction.CancelSelectionPointer)
        assertEquals(retained.workspaceState.selectionSession?.previewTransfer, session(runtime).previewTransfer)
    }

    @Test
    fun `display-relative composition is ordered and complete cycles restore geometric identity`() {
        val first = runtime()
        val second = runtime()
        listOf(first, second).forEach { runtime ->
            begin(runtime)
            select(runtime, position(0, 0), DocumentPoint.create(2, 1))
        }
        transform(first, PixelOrientation.RotateClockwise)
        transform(first, PixelOrientation.FlipHorizontal)
        transform(second, PixelOrientation.FlipHorizontal)
        transform(second, PixelOrientation.RotateClockwise)
        assertEquals(PixelOrientation.Transpose, session(first).previewTransfer?.orientation)
        assertEquals(PixelOrientation.Transverse, session(second).previewTransfer?.orientation)
        val cycles = runtime()
        begin(cycles)
        select(cycles, position(0, 0), DocumentPoint.create(2, 1))
        repeat(4) { transform(cycles, PixelOrientation.RotateClockwise) }
        assertTrue(session(cycles).previewTransfer?.isIdentity == true)
        assertFalse(session(cycles).canApply)
        repeat(2) { transform(cycles, PixelOrientation.FlipHorizontal) }
        assertTrue(session(cycles).previewTransfer?.isIdentity == true)
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            cycles.reduce(WorkspaceAction.TransformSelection(PixelOrientation.Identity)),
        )
    }

    @Test
    fun `source reselection starts identity and cancel restores the entire oriented draft`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime, position(0, 0), DocumentPoint.create(2, 1))
        transform(runtime, PixelOrientation.RotateClockwise)
        move(runtime, position(0, 0), DocumentPoint.create(5, 5))
        assertEquals(position(5, 3), session(runtime).targetRegion?.origin)
        assertEquals(PixelOrientation.RotateClockwise, session(runtime).previewTransfer?.orientation)
        val previous = session(runtime).previewTransfer
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(7, 5)))
        assertEquals(PixelOrientation.Identity, session(runtime).previewTransfer?.orientation)
        reduce(runtime, WorkspaceAction.CancelSelectionPointer)
        assertEquals(previous, session(runtime).previewTransfer)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(7, 5)))
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            runtime.reduce(WorkspaceAction.CancelSelectionPointer),
        )
        assertEquals(PixelOrientation.Identity, session(runtime).previewTransfer?.orientation)
        assertFalse(session(runtime).canApply)
    }

    @Test
    fun `transform requires a session and a released rectangle`() {
        val runtime = runtime()
        assertRejected(runtime, SelectionRejection.NoSession)
        begin(runtime)
        val empty = session(runtime)
        assertFalse(empty.canTransform(PixelOrientation.RotateClockwise))
        assertRejected(runtime, SelectionRejection.NoRectangle)
        assertSame(empty, session(runtime))
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(0, 0)))
        assertRejected(runtime, SelectionRejection.PointerActive)
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)
        assertTrue(session(runtime).canTransform(PixelOrientation.RotateClockwise))
    }

    private fun transform(
        runtime: EditorRuntime,
        operation: PixelOrientation,
    ) {
        reduce(runtime, WorkspaceAction.TransformSelection(operation))
    }

    private fun assertRejected(
        runtime: EditorRuntime,
        reason: SelectionRejection,
    ) {
        assertEquals(
            WorkspaceActionRejection.SelectionRejected(reason),
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.TransformSelection(PixelOrientation.RotateClockwise)),
            ).rejection,
        )
    }
}
