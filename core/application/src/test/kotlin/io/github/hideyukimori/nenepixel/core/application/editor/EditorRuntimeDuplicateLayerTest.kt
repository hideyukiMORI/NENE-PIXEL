package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.DuplicateLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.persistence.initializedFixture
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Issue #208: existing insertion reconciliation, clean checkpoint, autosave and runtime exclusions. */
internal class EditorRuntimeDuplicateLayerTest {
    @Test
    fun `duplicating another hidden row selects the exact copy and undo redo follow existing selection policy`() {
        val runtime = runtime()
        applied(runtime.execute(AddLayerCommand.create(runtime.captureSource(), layerId(1))))
        applied(runtime.execute(AddLayerCommand.create(runtime.captureSource(), layerId(2))))
        applied(
            runtime.execute(
                SetLayerVisibilityCommand.create(runtime.captureSource(), layerId(1), LayerVisibility.Hidden),
            ),
        )
        val before = runtime.state.documentState
        assertEquals(layerId(3), runtime.state.workspaceState.activeLayerId)
        duplicate(runtime, 1)

        val copied = runtime.state.documentState
        assertEquals(listOf(1, 4, 2, 3), copied.layers.map { it.id.value })
        assertEquals(layerId(4), runtime.state.workspaceState.activeLayerId)
        assertEquals(LayerVisibility.Hidden, copied.layers[1].visibility)
        assertSame(before.layers.first().snapshot, copied.layers[1].snapshot)
        undo(runtime)
        assertEquals(before, runtime.state.documentState)
        assertEquals(layerId(2), runtime.state.workspaceState.activeLayerId)
        redo(runtime)
        assertEquals(copied, runtime.state.documentState)
        assertEquals(layerId(4), runtime.state.workspaceState.activeLayerId)
    }

    @Test
    fun `one duplicate becomes dirty and autosavable while one undo restores the clean checkpoint`() {
        val runtime = runtime()
        val original = runtime.state.documentState
        duplicate(runtime, 1)
        val after = runtime.state.documentState
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
        assertNotNull(runtime.autosaveProjection.value.pendingStateToken)
        undo(runtime)
        assertEquals(original, runtime.state.documentState)
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
        assertEquals(HistoryAvailability.RedoAvailable, runtime.state.historyAvailability)
        redo(runtime)
        assertEquals(after, runtime.state.documentState)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
    }

    @Test
    fun `palette session excludes duplication without changing document workspace history or autosave`() {
        val runtime = runtime()
        runtime.paletteOperations.beginPaletteEdit()
        val before = runtime.state
        val autosave = runtime.autosaveProjection.value
        val result = runtime.execute(DuplicateLayerCommand.create(runtime.captureSource(), layerId(1)))
        assertEquals(CommandResult.Failed(CommandFailure.PaletteSessionActive), result)
        assertEquals(before, runtime.state)
        assertEquals(autosave, runtime.autosaveProjection.value)
    }

    @Test
    fun `an active document switch excludes duplication without any owner mutation`() =
        runBlocking {
            val runtime = initializedFixture().runtime
            assertInstanceOf(SwitchStart.Load::class.java, runtime.switchOperations.beginLoad())
            val before = runtime.state
            val autosave = runtime.autosaveProjection.value
            val result = runtime.execute(DuplicateLayerCommand.create(runtime.captureSource(), layerId(1)))
            assertEquals(CommandResult.Failed(CommandFailure.PersistenceBusy), result)
            assertEquals(before, runtime.state)
            assertEquals(autosave, runtime.autosaveProjection.value)
        }

    private fun runtime(): EditorRuntime = EditorRuntime.create(canvas(2, 1), defaultDefinition) { defaultDocumentId }

    private fun duplicate(
        runtime: EditorRuntime,
        id: Int,
    ) {
        applied(runtime.execute(DuplicateLayerCommand.create(runtime.captureSource(), layerId(id))))
    }

    private fun undo(runtime: EditorRuntime) {
        val document = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(document.id, document.revision)))
    }

    private fun redo(runtime: EditorRuntime) {
        val document = runtime.state.documentState
        applied(runtime.execute(RedoCommand.create(document.id, document.revision)))
    }
}
