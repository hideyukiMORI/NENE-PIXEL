package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.add
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.delete
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.move
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** History bytes retained by layer commands (ADR 0030): 32 bytes per transition plus what it keeps. */
internal class LayerCommandRetentionTest {
    @Test
    fun `add retains the transition header plus the empty new name`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(add(gateway, above = 1)))

        // 32 transition bytes + 0 UTF-8 bytes of the empty name.
        assertRetention(gateway, entries = 1, bytes = 32L)
    }

    @Test
    fun `rename retains the transition header plus the before and after name bytes`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(rename(gateway)))

        // 32 transition bytes + 6 bytes of "layer2" + 18 bytes of "線画レイヤー" (6 characters x 3 UTF-8 bytes).
        assertRetention(gateway, entries = 1, bytes = 56L)
    }

    @Test
    fun `move retains only the transition header`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(move(gateway, id = 1, toPosition = 2)))

        // 32 transition bytes; a move keeps no pixel or name bytes.
        assertRetention(gateway, entries = 1, bytes = 32L)
    }

    @Test
    fun `visibility change retains only the transition header`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(hide(gateway)))

        // 32 transition bytes; a visibility change keeps no pixel or name bytes.
        assertRetention(gateway, entries = 1, bytes = 32L)
    }

    @Test
    fun `delete retains the transition header plus the removed layer cells and name`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(delete(gateway, id = 2)))

        // 32 transition bytes + 2 index bytes + 1 coverage byte of the 2x1 layer + 6 bytes of "layer2".
        assertRetention(gateway, entries = 1, bytes = 41L)
    }

    @Test
    fun `the four layer commands add up in one history`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        applied(gateway.execute(add(gateway, above = 1)))
        applied(gateway.execute(rename(gateway)))
        applied(gateway.execute(move(gateway, id = 1, toPosition = 2)))
        applied(gateway.execute(hide(gateway)))

        // add 32 + rename 56 + move 32 + visibility 32.
        assertRetention(gateway, entries = 4, bytes = 152L)
    }

    private fun rename(gateway: CommandGateway): RenameLayerCommand =
        RenameLayerCommand.create(gateway.captureSource(), layerId(2), layerName("線画レイヤー"))

    private fun hide(gateway: CommandGateway): SetLayerVisibilityCommand =
        SetLayerVisibilityCommand.create(gateway.captureSource(), layerId(1), LayerVisibility.Hidden)

    private fun assertRetention(
        gateway: CommandGateway,
        entries: Int,
        bytes: Long,
    ) {
        val state = gateway.runtimeState
        assertEquals(entries, state.historyEntryCount)
        assertEquals(0, state.retainedHistoryChangeCount)
        assertEquals(bytes, state.retainedHistoryByteCount)
    }
}
