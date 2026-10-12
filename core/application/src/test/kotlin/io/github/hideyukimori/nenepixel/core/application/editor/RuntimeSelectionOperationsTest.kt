package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.assertSingleFrameCell
import io.github.hideyukimori.nenepixel.core.application.document.transition.singleFrameSnapshot
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.begin
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.move
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.paint
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.reduce
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.select
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.session
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class RuntimeSelectionOperationsTest {
    @Test
    fun `Apply commits once closes session and retains latest autosave capture`() {
        val runtime = runtime()
        paint(runtime)
        val initial = runtime.state.documentState
        val history = runtime.read { it.historyPosition() }
        val pending = runtime.autosaveProjection.value.pendingStateToken
        begin(runtime)
        select(runtime)
        move(runtime)
        assertEquals(pending, runtime.autosaveProjection.value.pendingStateToken)
        assertSame(initial, runtime.state.documentState)

        assertSame(SelectionApplyResult.Applied, runtime.selectionOperations.applySelection())

        val applied = runtime.state.documentState
        assertEquals(initial.revision.value + 1L, applied.revision.value)
        assertEquals(history.value + 1L, runtime.read { it.historyPosition().value })
        assertNull(runtime.state.workspaceState.selectionSession)
        assertSingleFrameCell(applied, position(0, 0), PixelCell.Empty)
        assertSingleFrameCell(applied, position(2, 1), PixelCell.Covered(redIndex))
        val expected = runtime.read { AutosaveStateToken(it.coordination.runtimeGeneration, it.historyPosition()) }
        assertNotEquals(pending, runtime.autosaveProjection.value.pendingStateToken)
        assertEquals(expected, runtime.autosaveProjection.value.pendingStateToken)
        assertSame(
            applied,
            runtime.read {
                it.coordination.autosave.pending
                    ?.document
            },
        )
    }

    @Test
    fun `NoChange Apply closes session but preserves redo checkpoint and autosave`() {
        val runtime = runtime()
        paint(runtime)
        val drawn = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(drawn.id, drawn.revision)))
        val before = runtime.state
        val autosave = runtime.autosaveProjection.value
        begin(runtime)
        select(runtime)
        move(runtime)

        assertSame(SelectionApplyResult.NoChange, runtime.selectionOperations.applySelection())

        assertSame(before.documentState, runtime.state.documentState)
        assertEquals(before.historyAvailability, runtime.state.historyAvailability)
        assertEquals(before.dirtyState, runtime.state.dirtyState)
        assertEquals(autosave, runtime.autosaveProjection.value)
        assertNull(runtime.state.workspaceState.selectionSession)
        applied(runtime.execute(RedoCommand.create(before.documentState.id, before.documentState.revision)))
        assertEquals(drawn, runtime.state.documentState)
    }

    @Test
    fun `entry uses canonical layer selection and settles stroke and competing modes`() {
        val runtime = runtime()
        applied(runtime.execute(AddLayerCommand.create(runtime.captureSource(), LayerId.first())))
        reduce(runtime, WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position(0, 0)))
        begin(runtime, LayerId.first())
        assertEquals(LayerId.first(), runtime.state.workspaceState.activeLayerId)
        assertNull(runtime.state.workspaceState.preview)
        assertEquals(CanvasPointerIntent.SelectPixels, runtime.state.workspaceState.canvasPointerIntent)
        reduce(runtime, WorkspaceAction.CancelSelectionSession)
        val underlay =
            ReferenceUnderlay
                .placed(
                    UnderlayMemoryFixtures.image(),
                    runtime.state.documentState.size,
                ).adjusting()
        reduce(runtime, WorkspaceAction.SetReferenceUnderlay(underlay))
        reduce(runtime, WorkspaceAction.OpenQuickSelect)
        reduce(runtime, WorkspaceAction.HighlightQuickSelectItem(QuickSelectItem.Eyedropper))
        reduce(runtime, WorkspaceAction.ConfirmQuickSelect)
        begin(runtime)

        assertEquals(
            UnderlayInteraction.Resting,
            runtime.state.workspaceState.underlay
                ?.interaction,
        )
        assertEquals(EyedropperState.Idle, runtime.state.workspaceState.quickSelection.eyedropper)
        assertNull(runtime.state.workspaceState.quickSelection.menu)
        assertEquals(CanvasPointerIntent.SelectPixels, runtime.state.workspaceState.canvasPointerIntent)
    }

    @Test
    fun `hidden layer and palette session entry refusal preserves every owner`() {
        val runtime = runtime()
        applied(
            runtime.execute(
                SetLayerVisibilityCommand.create(runtime.captureSource(), LayerId.first(), LayerVisibility.Hidden),
            ),
        )
        val hidden = runtime.state
        assertEquals(
            WorkspaceActionRejection.ActiveLayerHidden(LayerId.first()),
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.selectionOperations.beginSelection(LayerId.first()),
            ).rejection,
        )
        assertEquals(hidden, runtime.state)
        runtime.paletteOperations.beginPaletteEdit()
        val palette = runtime.state
        assertEquals(
            WorkspaceActionRejection.PaletteSessionActive,
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.selectionOperations.beginSelection(LayerId.first()),
            ).rejection,
        )
        assertEquals(palette, runtime.state)
        assertEquals(defaultDefinition, runtime.state.documentState.definition)
    }

    @Test
    fun `public and internal command successes cancel draft but a rejected command keeps it`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        val draft = session(runtime)
        val initial = runtime.state.documentState
        assertInstanceOf(
            CommandResult.Rejected::class.java,
            runtime.execute(UndoCommand.create(initial.id, initial.revision)),
        )
        assertSame(draft, session(runtime))
        paint(runtime)
        assertNull(runtime.state.workspaceState.selectionSession)
        begin(runtime)
        select(runtime)
        runtime.transact { transaction ->
            val result =
                transaction.executeCommand(
                    AddLayerCommand.create(transaction.captureSource(), LayerId.first()),
                )
            PersistenceTransition(transaction.coordination, result)
        }
        assertNull(runtime.state.workspaceState.selectionSession)
        begin(runtime, runtime.state.workspaceState.activeLayerId)
        select(runtime)
        val current = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(current.id, current.revision)))
        assertNull(runtime.state.workspaceState.selectionSession)
        begin(runtime)
        select(runtime)
        val undone = runtime.state.documentState
        applied(runtime.execute(RedoCommand.create(undone.id, undone.revision)))
        assertNull(runtime.state.workspaceState.selectionSession)
    }

    @Test
    fun `Apply refuses unfinished pointer without mutation and viewport restores retained draft`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        val retained = session(runtime).previewTransfer
        val document = runtime.state.documentState
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(2, 1)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(4, 3)))

        assertEquals(
            SelectionApplyResult.Rejected(WorkspaceActionRejection.SelectionRejected(SelectionRejection.PointerActive)),
            runtime.selectionOperations.applySelection(),
        )
        assertSame(document, runtime.state.documentState)
        reduce(runtime, WorkspaceAction.SetAppearance(runtime.state.workspaceState.appearance))
        assertEquals(retained, session(runtime).previewTransfer)
    }

    private fun applied(result: CommandResult) {
        assertInstanceOf(CommandResult.Applied::class.java, result)
    }
}
