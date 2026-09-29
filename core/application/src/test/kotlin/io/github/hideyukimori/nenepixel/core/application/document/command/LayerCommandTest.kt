package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.add
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.applyUndoRedo
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.delete
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.ids
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.move
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerName
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.smallCanvas
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LayerCommandTest {
    @Test
    fun `add inserts an empty unnamed visible layer directly above the target and round-trips`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        val after = applyUndoRedo(gateway, add(gateway, above = 1))

        assertEquals(listOf(1, 4, 2, 3), ids(after))
        val added = after.layers[1]
        assertEquals(LayerName.empty, added.name)
        assertEquals(LayerVisibility.Visible, added.visibility)
        assertEquals(PixelSnapshot.createEmpty(smallCanvas), added.snapshot)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    @Test
    fun `add above the top layer puts the new layer on top`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))

        val after = applyUndoRedo(gateway, add(gateway, above = 2))

        assertEquals(listOf(1, 2, 3), ids(after))
    }

    @Test
    fun `delete removes the layer and round-trips`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        val after = applyUndoRedo(gateway, delete(gateway, 2))

        assertEquals(listOf(1, 3), ids(after))
    }

    @Test
    fun `rename changes only the name and round-trips`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val command = RenameLayerCommand.create(gateway.captureSource(), layerId(2), layerName("線画"))

        val after = applyUndoRedo(gateway, command)

        assertEquals(layerName("線画"), after.layers[1].name)
        assertEquals(listOf(1, 2), ids(after))
    }

    @Test
    fun `move places the layer at the position counted after the move and round-trips`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        assertEquals(listOf(2, 3, 1), ids(applyUndoRedo(gateway, move(gateway, 1, 2))))
        assertEquals(listOf(1, 2, 3), ids(applyUndoRedo(gateway, move(gateway, 1, 0))))
    }

    @Test
    fun `set visibility changes only the visibility and round-trips`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val command =
            SetLayerVisibilityCommand.create(gateway.captureSource(), layerId(1), LayerVisibility.Hidden)

        val after = applyUndoRedo(gateway, command)

        assertEquals(LayerVisibility.Hidden, after.layers[0].visibility)
        assertEquals(LayerVisibility.Visible, after.layers[1].visibility)
    }
}
