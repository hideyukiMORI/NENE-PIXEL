package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.command.DeleteLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceNoChangeReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class EditorRuntimeLayerTest {
    private val size = canvas(2, 1)

    @Test
    fun `select layer moves the active layer and refuses a missing id without other effects`() {
        val runtime = runtimeWithLayers(3)
        val palette = runtime.state.workspaceState.activePaletteIndex
        val document = runtime.state.documentState

        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, select(runtime, 1))
        assertEquals(layerId(1), active(runtime))
        assertEquals(palette, runtime.state.workspaceState.activePaletteIndex)
        val again = assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, select(runtime, 1))
        assertEquals(WorkspaceNoChangeReason.ActiveLayerAlreadySelected, again.reason)
        val missing = assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, select(runtime, 9))
        assertEquals(WorkspaceActionRejection.LayerNotFound(layerId(9)), missing.rejection)
        assertEquals(layerId(1), active(runtime))
        assertEquals(document, runtime.state.documentState)
    }

    @Test
    fun `adding a layer selects the new layer`() {
        val runtime = runtimeWithLayers(2)
        assertEquals(layerId(2), active(runtime))

        select(runtime, 1)
        add(runtime, above = 1)

        assertEquals(listOf(1, 3, 2), ids(runtime))
        assertEquals(layerId(3), active(runtime))
    }

    @Test
    fun `deleting the active layer selects the layer now at its position clamped to the top`() {
        val runtime = runtimeWithLayers(4)

        select(runtime, 2)
        delete(runtime, 2)
        assertEquals(listOf(1, 3, 4), ids(runtime))
        assertEquals(layerId(3), active(runtime))

        select(runtime, 4)
        delete(runtime, 4)
        assertEquals(listOf(1, 3), ids(runtime))
        assertEquals(layerId(3), active(runtime))
    }

    @Test
    fun `deleting another layer keeps the selection`() {
        val runtime = runtimeWithLayers(3)

        delete(runtime, 1)

        assertEquals(listOf(2, 3), ids(runtime))
        assertEquals(layerId(3), active(runtime))
    }

    @Test
    fun `undoing an add selects the layer now at the removed position`() {
        val runtime = runtimeWithLayers(3)
        select(runtime, 1)
        add(runtime, above = 1)
        assertEquals(layerId(4), active(runtime))

        val document = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(document.id, document.revision)))

        assertEquals(listOf(1, 2, 3), ids(runtime))
        assertEquals(layerId(2), active(runtime))
    }

    @Test
    fun `a gesture is cancelled when its layer becomes hidden or is deleted`() {
        val runtime = runtimeWithLayers(2)
        begin(runtime)

        setVisibility(runtime, 1, LayerVisibility.Hidden)
        assertNotNull(runtime.state.workspaceState.preview)
        setVisibility(runtime, 2, LayerVisibility.Hidden)
        assertNull(runtime.state.workspaceState.preview)

        setVisibility(runtime, 2, LayerVisibility.Visible)
        begin(runtime)
        delete(runtime, 2)
        assertNull(runtime.state.workspaceState.preview)
        assertEquals(layerId(1), active(runtime))
    }

    @Test
    fun `a hidden active layer refuses drawing and eyedropper picks`() {
        val runtime = runtimeWithLayers(2)
        setVisibility(runtime, 2, LayerVisibility.Hidden)
        val before = runtime.state.workspaceState

        val drawing = assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, begin(runtime))
        assertEquals(WorkspaceActionRejection.ActiveLayerHidden(layerId(2)), drawing.rejection)
        assertEquals(before, runtime.state.workspaceState)

        runtime.reduce(WorkspaceAction.OpenQuickSelect)
        runtime.reduce(WorkspaceAction.HighlightQuickSelectItem(QuickSelectItem.Eyedropper))
        runtime.reduce(WorkspaceAction.ConfirmQuickSelect)
        val armed = runtime.state.workspaceState
        assertEquals(EyedropperState.Armed, armed.quickSelection.eyedropper)
        val pick =
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.PickPaletteEntryAt(position(0, 0))),
            )
        assertEquals(WorkspaceActionRejection.ActiveLayerHidden(layerId(2)), pick.rejection)
        assertEquals(armed, runtime.state.workspaceState)
    }

    @Test
    fun `a stroke on a hidden layer is rejected by the command`() {
        val runtime = runtimeWithLayers(2)
        setVisibility(runtime, 2, LayerVisibility.Hidden)
        val document = runtime.state.documentState

        val stroke = stroke(size, listOf(position(0, 0)), redIndex)
        val command = ApplyStrokeCommand.create(runtime.captureSource(), layerId(2), stroke)

        assertEquals(RejectionReason.LayerHidden(layerId(2)), rejected(runtime.execute(command)))
        assertEquals(document, runtime.state.documentState)
    }

    @Test
    fun `layer commands mark the runtime dirty only when applied`() {
        val runtime = runtime()

        val last = DeleteLayerCommand.create(runtime.captureSource(), layerId(1))
        assertEquals(RejectionReason.LastLayerNotDeletable, rejected(runtime.execute(last)))
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
        val same = SetLayerVisibilityCommand.create(runtime.captureSource(), layerId(1), LayerVisibility.Visible)
        assertEquals(RejectionReason.NoEffectiveChange, rejected(runtime.execute(same)))
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)

        add(runtime, above = 1)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
    }

    private fun runtime(): EditorRuntime = EditorRuntime.create(size, defaultDefinition) { defaultDocumentId }

    /** Layers `1..count` bottom first, built by adding at the top; the top one is active. */
    private fun runtimeWithLayers(count: Int): EditorRuntime {
        val runtime = runtime()
        for (below in 1 until count) {
            add(runtime, above = below)
        }
        return runtime
    }

    private fun add(
        runtime: EditorRuntime,
        above: Int,
    ) {
        applied(runtime.execute(AddLayerCommand.create(runtime.captureSource(), layerId(above))))
    }

    private fun delete(
        runtime: EditorRuntime,
        id: Int,
    ) {
        applied(runtime.execute(DeleteLayerCommand.create(runtime.captureSource(), layerId(id))))
    }

    private fun setVisibility(
        runtime: EditorRuntime,
        id: Int,
        visibility: LayerVisibility,
    ) {
        applied(runtime.execute(SetLayerVisibilityCommand.create(runtime.captureSource(), layerId(id), visibility)))
    }

    private fun select(
        runtime: EditorRuntime,
        id: Int,
    ): WorkspaceReductionResult = runtime.reduce(WorkspaceAction.SelectLayer(layerId(id)))

    private fun begin(runtime: EditorRuntime): WorkspaceReductionResult =
        runtime.reduce(WorkspaceAction.BeginGesturePreview(size, position(0, 0)))

    private fun active(runtime: EditorRuntime): LayerId = runtime.state.workspaceState.activeLayerId

    private fun ids(runtime: EditorRuntime): List<Int> =
        runtime.state.documentState.layers
            .map { it.id.value }
}
