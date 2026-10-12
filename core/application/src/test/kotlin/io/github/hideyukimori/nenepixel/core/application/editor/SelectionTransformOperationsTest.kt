package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.assertSingleFrameCell
import io.github.hideyukimori.nenepixel.core.application.document.transition.singleFrameSnapshot
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.begin
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.paint
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.reduce
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.select
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.session
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SelectionTransformOperationsTest {
    @Test
    fun `in-place non-square rotation is one ordinary Apply and exact undo redo`() {
        val runtime = runtime()
        paint(runtime)
        val before = runtime.state.documentState
        val history = runtime.read { it.historyPosition() }
        val autosave = runtime.autosaveProjection.value
        begin(runtime)
        select(runtime, position(0, 0), DocumentPoint.create(2, 1))
        reduce(runtime, WorkspaceAction.TransformSelection(PixelOrientation.RotateClockwise))
        assertTrue(session(runtime).canApply)
        assertSame(before, runtime.state.documentState)
        assertEquals(autosave, runtime.autosaveProjection.value)
        assertSame(SelectionApplyResult.Applied, runtime.selectionOperations.applySelection())
        val after = runtime.state.documentState
        assertEquals(history.value + 1L, runtime.read { it.historyPosition().value })
        assertEquals(before.revision.value + 1L, after.revision.value)
        assertSingleFrameCell(after, position(1, 0), PixelCell.Covered(redIndex))
        assertSingleFrameCell(after, position(0, 0), PixelCell.Empty)
        assertSingleFrameCell(after, position(2, 0), PixelCell.Empty)
        assertNull(runtime.state.workspaceState.selectionSession)
        assertEquals(
            runtime.read { AutosaveStateToken(it.coordination.runtimeGeneration, it.historyPosition()) },
            runtime.autosaveProjection.value.pendingStateToken,
        )
        applied(runtime.execute(UndoCommand.create(after.id, after.revision)))
        assertEquals(before, runtime.state.documentState)
        applied(runtime.execute(RedoCommand.create(before.id, before.revision)))
        assertEquals(after, runtime.state.documentState)
    }

    @Test
    fun `equal-content transformed Copy closes without truncating redo or altering autosave`() {
        val runtime = runtime()
        paint(runtime)
        val painted = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(painted.id, painted.revision)))
        val before = runtime.state
        val autosave = runtime.autosaveProjection.value
        begin(runtime)
        select(runtime, position(0, 0), DocumentPoint.create(2, 1))
        reduce(runtime, WorkspaceAction.SetSelectionMode(PixelTransferMode.Copy))
        reduce(runtime, WorkspaceAction.TransformSelection(PixelOrientation.RotateClockwise))
        assertTrue(session(runtime).canApply)
        assertSame(SelectionApplyResult.NoChange, runtime.selectionOperations.applySelection())
        assertSame(before.documentState, runtime.state.documentState)
        assertEquals(before.historyAvailability, runtime.state.historyAvailability)
        assertEquals(before.dirtyState, runtime.state.dirtyState)
        assertEquals(autosave, runtime.autosaveProjection.value)
        assertNull(runtime.state.workspaceState.selectionSession)
        applied(runtime.execute(RedoCommand.create(painted.id, before.documentState.revision)))
        assertEquals(painted, runtime.state.documentState)
    }

    private fun applied(result: CommandResult): CommandResult.Applied =
        assertInstanceOf(CommandResult.Applied::class.java, result)
}
