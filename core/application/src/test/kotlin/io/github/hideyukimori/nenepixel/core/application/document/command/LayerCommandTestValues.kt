package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import org.junit.jupiter.api.Assertions.assertEquals

internal object LayerCommandTestValues {
    private const val MISSING_ID: Int = 99

    /** Layers with ids [ids], bottom first, each named `layer<id>` on the 2x1 canvas. */
    fun stateWithIds(vararg ids: Int): DocumentState = layeredState(ids.map { layer(it, "layer$it") })

    fun ids(state: DocumentState): List<Int> = state.layers.map { it.id.value }

    fun missing(): LayerId = layerId(MISSING_ID)

    fun add(
        gateway: CommandGateway,
        above: Int,
    ): AddLayerCommand = AddLayerCommand.create(gateway.captureSource(), layerId(above))

    fun delete(
        gateway: CommandGateway,
        id: Int,
    ): DeleteLayerCommand = DeleteLayerCommand.create(gateway.captureSource(), layerId(id))

    fun move(
        gateway: CommandGateway,
        id: Int,
        toPosition: Int,
    ): MoveLayerCommand = MoveLayerCommand.create(gateway.captureSource(), layerId(id), toPosition)

    fun undo(gateway: CommandGateway) {
        val state = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(state.id, state.revision)))
    }

    fun redo(gateway: CommandGateway) {
        val state = gateway.runtimeState.documentState
        applied(gateway.execute(RedoCommand.create(state.id, state.revision)))
    }

    /** Applies [command], checks undo restores the original and redo the applied state, and returns the latter. */
    fun applyUndoRedo(
        gateway: CommandGateway,
        command: DocumentCommand,
    ): DocumentState {
        val original = gateway.runtimeState.documentState
        applied(gateway.execute(command))
        val after = gateway.runtimeState.documentState
        assertEquals(original.revision.value + 1L, after.revision.value)
        undo(gateway)
        assertEquals(original, gateway.runtimeState.documentState)
        redo(gateway)
        assertEquals(after, gateway.runtimeState.documentState)
        return after
    }

    /** Asserts [command] is rejected with [expected] and leaves document, revision, history and retention untouched. */
    fun assertRejectedWithoutEffect(
        gateway: CommandGateway,
        command: DocumentCommand,
        expected: RejectionReason,
    ) {
        val before = gateway.runtimeState
        assertEquals(expected, rejected(gateway.execute(command)))
        assertEquals(before, gateway.runtimeState)
    }
}
