package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.add
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.assertRejectedWithoutEffect
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.delete
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.missing
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.move
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerName
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LayerCommandRejectionTest {
    @Test
    fun `every layer command rejects a missing target layer without effect`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val source = gateway.captureSource()
        val notFound = RejectionReason.LayerNotFound(missing())

        assertRejectedWithoutEffect(gateway, AddLayerCommand.create(source, missing()), notFound)
        assertRejectedWithoutEffect(gateway, DeleteLayerCommand.create(source, missing()), notFound)
        assertRejectedWithoutEffect(gateway, RenameLayerCommand.create(source, missing(), layerName("x")), notFound)
        assertRejectedWithoutEffect(gateway, MoveLayerCommand.create(source, missing(), 0), notFound)
        assertRejectedWithoutEffect(
            gateway,
            SetLayerVisibilityCommand.create(source, missing(), LayerVisibility.Hidden),
            notFound,
        )
    }

    @Test
    fun `add reaches sixteen layers and rejects the seventeenth`() {
        val gateway = CommandGateway.create(stateWithIds(1))
        repeat(LayerLimits.MAX_LAYERS - 1) { applied(gateway.execute(add(gateway, above = 1))) }
        assertEquals(LayerLimits.MAX_LAYERS, gateway.runtimeState.documentState.layers.size)

        assertRejectedWithoutEffect(gateway, add(gateway, above = 1), RejectionReason.LayerLimitReached)
    }

    @Test
    fun `add rejects when the next layer id would overflow`() {
        val gateway = CommandGateway.create(layeredState(listOf(layer(1, "a"), layer(Int.MAX_VALUE, "b"))))

        assertRejectedWithoutEffect(gateway, add(gateway, above = 1), RejectionReason.LayerIdOverflow)
    }

    @Test
    fun `delete rejects the last remaining layer`() {
        val gateway = CommandGateway.create(stateWithIds(5))

        assertRejectedWithoutEffect(gateway, delete(gateway, 5), RejectionReason.LastLayerNotDeletable)
    }

    @Test
    fun `move rejects positions outside the layer list`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2, 3))

        assertRejectedWithoutEffect(gateway, move(gateway, 1, -1), RejectionReason.LayerPositionOutOfRange(-1, 3))
        assertRejectedWithoutEffect(gateway, move(gateway, 1, 3), RejectionReason.LayerPositionOutOfRange(3, 3))
    }

    @Test
    fun `move rename and visibility to the current value are no effective change`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val source = gateway.captureSource()

        assertRejectedWithoutEffect(gateway, move(gateway, 2, 1), RejectionReason.NoEffectiveChange)
        assertRejectedWithoutEffect(
            gateway,
            RenameLayerCommand.create(source, layerId(1), layerName("layer1")),
            RejectionReason.NoEffectiveChange,
        )
        assertRejectedWithoutEffect(
            gateway,
            SetLayerVisibilityCommand.create(source, layerId(1), LayerVisibility.Visible),
            RejectionReason.NoEffectiveChange,
        )
    }

    @Test
    fun `layer commands reject a revision overflow`() {
        val state = layeredState(listOf(layer(1, "a"), layer(2, "b")), revision(Long.MAX_VALUE))
        val gateway = CommandGateway.create(state)

        assertRejectedWithoutEffect(gateway, add(gateway, above = 1), RejectionReason.RevisionOverflow)
        assertRejectedWithoutEffect(gateway, delete(gateway, 1), RejectionReason.RevisionOverflow)
    }

    @Test
    fun `layer commands reject a stale source admission`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val stale = gateway.captureSource()
        applied(gateway.execute(delete(gateway, 2)))

        assertRejectedWithoutEffect(
            gateway,
            AddLayerCommand.create(stale, layerId(1)),
            RejectionReason.SourceHistoryMismatch,
        )
    }
}
