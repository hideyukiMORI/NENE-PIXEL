package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
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
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SelectionSessionTest {
    @Test
    fun `reverse rectangle is inclusive clamps to edges and changes no committed owners`() {
        val runtime = runtime()
        val before = runtime.state
        begin(runtime)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(4, 3)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(Int.MIN_VALUE, Int.MIN_VALUE)))
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)
        val selection = session(runtime)

        assertEquals(position(0, 0), selection.sourceRegion?.origin)
        assertEquals(
            5,
            selection.sourceRegion
                ?.size
                ?.width
                ?.value,
        )
        assertEquals(
            4,
            selection.sourceRegion
                ?.size
                ?.height
                ?.value,
        )
        assertEquals(selection.sourceRegion, selection.targetRegion)
        assertFalse(selection.canApply)
        assertEquals(SelectionInteraction.Idle, selection.interaction)
        assertSame(before.documentState, runtime.state.documentState)
        assertEquals(before.dirtyState, runtime.state.dirtyState)
    }

    @Test
    fun `whole rectangle translation clamps signed extreme samples without overflow`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(1, 1)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(Int.MAX_VALUE, Int.MIN_VALUE)))
        assertEquals(SelectionInteraction.Translating, session(runtime).interaction)
        assertEquals(position(6, 0), session(runtime).targetRegion?.origin)
        assertFalse(session(runtime).canApply)
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(Int.MIN_VALUE, Int.MAX_VALUE)))
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)

        assertEquals(position(0, 4), session(runtime).targetRegion?.origin)
        assertEquals(
            2,
            session(runtime)
                .targetRegion
                ?.size
                ?.width
                ?.value,
        )
        assertTrue(session(runtime).canApply)
        assertSame(session(runtime).previewTransfer, session(runtime).previewTransfer)
    }

    @Test
    fun `pointer cancellation restores prior offset and is harmless after successful release`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        val retained = session(runtime)
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            runtime.reduce(WorkspaceAction.CancelSelectionPointer),
        )
        assertSame(retained, session(runtime))
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(2, 1)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(5, 3)))
        reduce(runtime, WorkspaceAction.CancelSelectionPointer)

        assertEquals(retained.previewTransfer, session(runtime).previewTransfer)
        assertEquals(SelectionInteraction.Idle, session(runtime).interaction)
    }

    @Test
    fun `outside down starts new rectangle while cancel restores the old retained draft`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        val before = session(runtime)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(7, 5)))
        assertEquals(SelectionInteraction.Selecting, session(runtime).interaction)
        assertEquals(position(7, 5), session(runtime).sourceRegion?.origin)
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(5, 4)))
        reduce(runtime, WorkspaceAction.CancelSelectionPointer)
        assertEquals(before.previewTransfer, session(runtime).previewTransfer)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(7, 5)))
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)
        assertEquals(position(7, 5), session(runtime).sourceRegion?.origin)
        assertFalse(session(runtime).canApply)
    }

    @Test
    fun `mode change restores an unfinished drag and preserves completed geometry`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        val previous = session(runtime).targetRegion
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(2, 1)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(6, 4)))
        reduce(runtime, WorkspaceAction.SetSelectionMode(PixelTransferMode.Copy))

        assertEquals(previous, session(runtime).targetRegion)
        assertEquals(PixelTransferMode.Copy, session(runtime).mode)
        assertEquals(SelectionInteraction.Idle, session(runtime).interaction)
        assertTrue(session(runtime).canApply)
    }

    @Test
    fun `same viewport cancels only unfinished pointer and actual size keeps it`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        val previous = session(runtime).targetRegion
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(2, 1)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(5, 3)))
        runtime.reduce(WorkspaceAction.SetActualSizeWindow(ActualSizeWindow.initial))
        assertEquals(SelectionInteraction.Translating, session(runtime).interaction)
        reduce(runtime, WorkspaceAction.SetViewport(runtime.state.workspaceState.viewport))

        assertEquals(previous, session(runtime).targetRegion)
        assertEquals(SelectionInteraction.Idle, session(runtime).interaction)
    }

    @Test
    fun `absent and conflicting pointers reject atomically but cancellation is idempotent`() {
        val runtime = runtime()
        assertEquals(
            WorkspaceActionRejection.SelectionRejected(SelectionRejection.NoSession),
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(0, 0))),
            ).rejection,
        )
        begin(runtime)
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(0, 0)))
        val active = session(runtime)
        assertEquals(
            WorkspaceActionRejection.SelectionRejected(SelectionRejection.PointerAlreadyActive),
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(1, 0))),
            ).rejection,
        )
        assertSame(active, session(runtime))
        reduce(runtime, WorkspaceAction.CancelSelectionPointer)
        assertNull(session(runtime).sourceRegion)
        reduce(runtime, WorkspaceAction.CancelSelectionSession)
        assertNull(runtime.state.workspaceState.selectionSession)
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            runtime.reduce(WorkspaceAction.CancelSelectionPointer),
        )
    }
}
