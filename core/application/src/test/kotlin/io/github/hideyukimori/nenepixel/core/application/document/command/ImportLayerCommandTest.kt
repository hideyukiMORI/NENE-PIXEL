package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.addingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.convertingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.import
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.applyUndoRedo
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.ids
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.undo
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [ImportLayerCommand] adds one layer that already holds its pixels, with its palette, as one history entry. */
internal class ImportLayerCommandTest {
    @Test
    fun `adding form inserts the planned layer above the target and appends the colours in one entry`() {
        val original = stateWithIds(1, 2, 3)
        val gateway = CommandGateway.create(original)
        val plan = addingPlan()

        val after = applyUndoRedo(gateway, import(gateway, above = 1, plan))

        assertEquals(listOf(1, 4, 2, 3), ids(after))
        assertImportedLayer(after, plan, id = 4)
        assertEquals(plan.target, after.definition)
        assertOtherLayersKept(original, after)
        assertEquals(1, gateway.runtimeState.historyEntryCount)
    }

    @Test
    fun `undo of the adding form removes the layer and restores the source palette`() {
        val original = stateWithIds(1, 2)
        val gateway = CommandGateway.create(original)
        val plan = addingPlan()

        applyUndoRedo(gateway, import(gateway, above = 2, plan))
        undo(gateway)

        val undone = gateway.runtimeState.documentState
        assertEquals(listOf(1, 2), ids(undone))
        assertEquals(plan.source, undone.definition)
        assertEquals(original.layers, undone.layers)
    }

    @Test
    fun `converting form keeps the palette and still adds the layer in one entry`() {
        val original = stateWithIds(1, 2)
        val gateway = CommandGateway.create(original)
        val plan = convertingPlan()

        val after = applyUndoRedo(gateway, import(gateway, above = 2, plan))

        assertEquals(listOf(1, 2, 3), ids(after))
        assertImportedLayer(after, plan, id = 3)
        assertEquals(defaultDefinition, after.definition)
        assertOtherLayersKept(original, after)
        assertEquals(1, gateway.runtimeState.historyEntryCount)
    }

    private fun assertImportedLayer(
        state: DocumentState,
        plan: LayerImportPlan,
        id: Int,
    ) {
        val imported = state.layers.single { it.id == layerId(id) }
        assertEquals(plan.snapshot, imported.snapshot)
        assertEquals(LayerName.empty, imported.name)
        assertEquals(LayerVisibility.Visible, imported.visibility)
    }

    private fun assertOtherLayersKept(
        original: DocumentState,
        after: DocumentState,
    ) {
        for (layer in original.layers) {
            assertEquals(layer, after.layers.single { it.id == layer.id })
        }
    }
}
