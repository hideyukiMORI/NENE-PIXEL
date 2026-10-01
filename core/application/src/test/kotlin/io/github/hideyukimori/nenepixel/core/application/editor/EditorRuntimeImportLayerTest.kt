package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.addingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.appendedIndex
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.convertingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The existing selection policies follow an [ImportLayerCommand] without import-specific rules (ADR 0033). */
internal class EditorRuntimeImportLayerTest {
    @Test
    fun `the imported layer becomes the active layer`() {
        val runtime = runtimeWithTwoLayers()
        runtime.reduce(WorkspaceAction.SelectLayer(layerId(1)))

        import(runtime, above = 1, convertingPlan())

        val ids =
            runtime.state.documentState.layers
                .map { it.id.value }
        assertEquals(listOf(1, 3, 2), ids)
        assertEquals(layerId(3), runtime.state.workspaceState.activeLayerId)
    }

    @Test
    fun `undo returns a selected appended colour to the palette range`() {
        val runtime = runtimeWithTwoLayers()
        val plan = addingPlan()

        import(runtime, above = 2, plan)
        runtime.reduce(WorkspaceAction.SelectPaletteEntry(appendedIndex))
        assertEquals(appendedIndex, runtime.state.workspaceState.activePaletteIndex)

        val document = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(document.id, document.revision)))

        assertEquals(plan.source, runtime.state.documentState.definition)
        assertEquals(defaultDefinition.defaultIndex, runtime.state.workspaceState.activePaletteIndex)
    }

    private fun runtimeWithTwoLayers(): EditorRuntime {
        val runtime = EditorRuntime.create(canvas(2, 1), defaultDefinition) { defaultDocumentId }
        applied(runtime.execute(AddLayerCommand.create(runtime.captureSource(), layerId(1))))
        return runtime
    }

    private fun import(
        runtime: EditorRuntime,
        above: Int,
        plan: LayerImportPlan,
    ) {
        applied(runtime.execute(ImportLayerCommand.create(runtime.captureSource(), layerId(above), plan)))
    }
}
