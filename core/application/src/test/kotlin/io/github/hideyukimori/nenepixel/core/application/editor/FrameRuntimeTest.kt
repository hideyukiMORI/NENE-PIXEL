package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddFrameCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.DeleteFrameCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.frame
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.required
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class FrameRuntimeTest {
    @Test fun `frame focus cancels old pointer atomically keeps palette draft and never dirties`() {
        val runtime = runtime(state(frame(1, listOf(layer(1, "a"), layer(2, "b"))), frame(2, listOf(layer(9, "c")))))
        assertEquals(id(1), runtime.state.workspaceState.activeFrameId)
        assertEquals(layerId(2), runtime.state.workspaceState.activeLayerId)
        val before = runtime.state
        val autosave = runtime.autosaveProjection.value
        runtime.reduce(WorkspaceAction.BeginGesturePreview(before.documentState.size, position(0, 0)))
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            runtime.reduce(WorkspaceAction.SelectFrame(id(2))),
        )
        assertNull(runtime.state.workspaceState.preview)
        assertEquals(layerId(9), runtime.state.workspaceState.activeLayerId)
        assertEquals(before.dirtyState, runtime.state.dirtyState)
        assertEquals(autosave, runtime.autosaveProjection.value)
        runtime.paletteOperations.beginPaletteEdit()
        runtime.reduce(
            WorkspaceAction.EditPaletteDraft(
                io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation.SetSlotColor(
                    redIndex,
                    io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green,
                ),
            ),
        )
        val draft = requireNotNull(runtime.state.workspaceState.paletteEditSession)
        assertEquals(true, draft.canUndoDraft)
        runtime.reduce(WorkspaceAction.SelectFrame(id(1)))
        assertSame(draft, runtime.state.workspaceState.paletteEditSession)
        runtime.reduce(WorkspaceAction.BeginPaletteColorEdit(blackIndex))
        val active = runtime.state.workspaceState
        assertEquals(
            WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.ColorEditActive),
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.SelectFrame(id(2))),
            ).rejection,
        )
        assertSame(active, runtime.state.workspaceState)
    }

    @Test fun `frame switch settles selection quick select and underlay adjustment through canonical owners`() {
        val runtime = runtime(state(frame(1, listOf(layer(1, "a"))), frame(2, listOf(layer(9, "b")))))
        runtime.selectionOperations.beginSelection(layerId(1))
        runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(0, 0)))
        runtime.reduce(WorkspaceAction.SelectFrame(id(2)))
        assertNull(runtime.state.workspaceState.selectionSession)
        runtime.reduce(WorkspaceAction.OpenQuickSelect)
        runtime.reduce(WorkspaceAction.SelectFrame(id(1)))
        assertNull(runtime.state.workspaceState.quickSelection.menu)
        val underlay =
            io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
                .placed(
                    UnderlayMemoryFixtures.image(),
                    runtime.state.documentState.size,
                ).adjusting()
        runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
        runtime.reduce(WorkspaceAction.SelectFrame(id(2)))
        assertEquals(
            io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction.Resting,
            runtime.state.workspaceState.underlay
                ?.interaction,
        )
        assertSame(
            underlay.image,
            runtime.state.workspaceState.underlay
                ?.image,
        )
    }

    @Test fun `insert delete undo and redo reconcile both frame and layer under the command lock`() {
        val original = state(frame(1, listOf(layer(1, "a"))), frame(2, listOf(layer(9, "b"))))
        val runtime = runtime(original)
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(AddFrameCommand.create(runtime.captureSource(), id(1))),
        )
        assertEquals(id(3), runtime.state.workspaceState.activeFrameId)
        assertEquals(layerId(10), runtime.state.workspaceState.activeLayerId)
        val expected = runtime.read { AutosaveStateToken(it.coordination.runtimeGeneration, it.historyPosition()) }
        assertEquals(expected, runtime.autosaveProjection.value.pendingStateToken)
        runtime.execute(DeleteFrameCommand.create(runtime.captureSource(), id(3)))
        assertEquals(id(2), runtime.state.workspaceState.activeFrameId)
        val deleted = runtime.state.documentState
        runtime.execute(UndoCommand.create(deleted.id, deleted.revision))
        assertEquals(id(3), runtime.state.workspaceState.activeFrameId)
        val inserted = runtime.state.documentState
        runtime.execute(UndoCommand.create(inserted.id, inserted.revision))
        assertEquals(original, runtime.state.documentState)
        assertEquals(id(2), runtime.state.workspaceState.activeFrameId)
        runtime.execute(RedoCommand.create(original.id, original.revision))
        assertEquals(id(3), runtime.state.workspaceState.activeFrameId)
    }

    @Test fun `cross-frame layer selection rejects and inserted non-active frame layer selects its owner`() {
        val runtime = runtime(state(frame(1, listOf(layer(1, "a"))), frame(2, listOf(layer(9, "b")))))
        val before = runtime.state.workspaceState
        assertEquals(
            WorkspaceActionRejection.LayerNotInActiveFrame(layerId(9), id(1)),
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.SelectLayer(layerId(9))),
            ).rejection,
        )
        assertSame(before, runtime.state.workspaceState)
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(AddLayerCommand.create(runtime.captureSource(), layerId(9))),
        )
        assertEquals(id(2), runtime.state.workspaceState.activeFrameId)
        assertEquals(layerId(10), runtime.state.workspaceState.activeLayerId)
        val selected = runtime.state.workspaceState
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            runtime.reduce(WorkspaceAction.SelectFrame(id(2))),
        )
        assertSame(selected, runtime.state.workspaceState)
    }

    @Test fun `one palette command remaps hidden non-active frames and undo restores the whole hierarchy`() {
        val pixels =
            snapshot(
                canvas(2, 1),
                listOf(redIndex, redIndex),
            )
        val original =
            state(
                frame(1, listOf(layer(1, "a", pixels))),
                frame(2, listOf(layer(9, "b", pixels, LayerVisibility.Hidden))),
            )
        val runtime = runtime(original)
        val remap =
            required(
                PaletteRemap.create(
                    original.definition,
                    original.definition,
                    listOf(blackIndex, greenIndex, greenIndex),
                ),
            )
        val changeSet = applied(runtime.execute(ReplacePaletteCommand.create(runtime.captureSource(), remap)))
        assertEquals(listOf(layerId(1), layerId(9)), changeSet.layerChanges.map { it.layerId })
        runtime.state.documentState.frames.forEach { frame ->
            assertEquals(
                listOf(2, 2),
                frame.layers
                    .single()
                    .snapshot
                    .copyPackedIndices()
                    .map { it.toInt() },
            )
        }
        assertEquals(id(1), runtime.state.workspaceState.activeFrameId)
        val after = runtime.state.documentState
        runtime.execute(UndoCommand.create(after.id, after.revision))
        assertEquals(original, runtime.state.documentState)
    }
}
