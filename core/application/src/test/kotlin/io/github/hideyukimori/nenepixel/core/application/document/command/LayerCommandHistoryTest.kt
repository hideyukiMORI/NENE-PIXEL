package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.add
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.delete
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.ids
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.move
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.undo
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LayerCommandHistoryTest {
    @Test
    fun `undo restores a deleted 256x256 layer with the same id position and pixels`() {
        val large = canvas(LARGE_SIDE, LARGE_SIDE)
        val pixels = snapshot(large, List(LARGE_SIDE * LARGE_SIDE) { if (it % 3 == 0) redIndex else greenIndex })
        val original =
            layeredState(
                listOf(
                    layer(1, "bottom", snapshot(large)),
                    layer(2, "背景", pixels),
                    layer(3, "top", snapshot(large)),
                ),
            )
        val gateway = CommandGateway.create(original)

        applied(gateway.execute(delete(gateway, 2)))
        assertEquals(listOf(1, 3), ids(gateway.runtimeState.documentState))
        undo(gateway)

        val restored = gateway.runtimeState.documentState
        assertEquals(original, restored)
        assertEquals(layerId(2), restored.layers[1].id)
        assertEquals(pixels, restored.layers[1].snapshot)
    }

    @Test
    fun `add uses the largest id plus one and never reuses a deleted id`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(delete(gateway, 2)))
        applied(gateway.execute(add(gateway, above = 1)))

        assertEquals(listOf(1, 4, 3), ids(gateway.runtimeState.documentState))
    }

    @Test
    fun `strokes and layer commands share one linear history undone newest first`() {
        val initial = stateWithIds(1, 2)
        val gateway = CommandGateway.create(initial)
        val paint =
            ApplyStrokeCommand.create(
                gateway.captureSource(),
                layerId(2),
                stroke(initial.size, listOf(position(0, 0)), redIndex),
            )
        applied(gateway.execute(paint))
        val afterA = gateway.runtimeState.documentState
        applied(gateway.execute(add(gateway, above = 2)))
        val afterB = gateway.runtimeState.documentState
        applied(gateway.execute(move(gateway, 3, 0)))
        assertEquals(listOf(3, 1, 2), ids(gateway.runtimeState.documentState))

        undo(gateway)
        assertEquals(afterB, gateway.runtimeState.documentState)
        undo(gateway)
        assertEquals(afterA, gateway.runtimeState.documentState)
        undo(gateway)
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
    }

    private companion object {
        const val LARGE_SIDE: Int = 256
    }
}
