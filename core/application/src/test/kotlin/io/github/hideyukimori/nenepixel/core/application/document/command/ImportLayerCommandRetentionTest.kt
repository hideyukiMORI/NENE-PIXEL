package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.addingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.convertingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.import
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTransition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** History bytes retained by an import (ADR 0033 amending ADR 0030): a layer with pixels is charged like a delete. */
internal class ImportLayerCommandRetentionTest {
    @Test
    fun `adding form retains the header the layer cells and both palettes`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))

        applied(gateway.execute(import(gateway, above = 1, addingPlan())))

        // 32 transition bytes + 2 index bytes + 1 coverage byte + 0 name bytes
        // + palette 4 x (3 + 4) colour bytes + 8 default bytes.
        assertRetention(gateway, bytes = 71L)
    }

    @Test
    fun `converting form retains the header and the layer cells only`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))

        applied(gateway.execute(import(gateway, above = 1, convertingPlan())))

        // 32 transition bytes + 2 index bytes + 1 coverage byte + 0 name bytes; the palette is unchanged.
        assertRetention(gateway, bytes = 35L)
    }

    @Test
    fun `an added layer with pixels and its inverse charge the same`() {
        val added = LayerStructureTransition.Added(layer(4, "ab", addingPlan().snapshot), position = 1)

        // 2 index bytes + 1 coverage byte + 2 name bytes, in both directions.
        assertEquals(5L, added.retainedByteCount)
        assertEquals(added.retainedByteCount, added.inverse().retainedByteCount)
    }

    @Test
    fun `an added all-Empty layer keeps the name-only charge`() {
        val added = LayerStructureTransition.Added(layer(4, "ab"), position = 1)

        assertEquals(2L, added.retainedByteCount)
    }

    private fun assertRetention(
        gateway: CommandGateway,
        bytes: Long,
    ) {
        val state = gateway.runtimeState
        assertEquals(1, state.historyEntryCount)
        assertEquals(0, state.retainedHistoryChangeCount)
        assertEquals(bytes, state.retainedHistoryByteCount)
    }
}
