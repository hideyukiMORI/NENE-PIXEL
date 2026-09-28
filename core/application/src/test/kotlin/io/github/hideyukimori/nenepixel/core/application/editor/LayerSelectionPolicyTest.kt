package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LayerSelectionPolicyTest {
    private val none = LayerStructureTransition.None

    @Test
    fun `install selects the top layer`() {
        assertEquals(layerId(3), LayerSelectionPolicy.onInstall(document(1, 2, 3)))
        assertEquals(layerId(1), LayerSelectionPolicy.onInstall(document(1)))
    }

    @Test
    fun `a present active layer is kept after a command`() {
        val document = document(1, 2)

        assertNull(LayerSelectionPolicy.afterApplied(layerId(1), document, undo(document), none))
        assertNull(LayerSelectionPolicy.afterApplied(layerId(2), document, undo(document), none))
    }

    @Test
    fun `an added layer is selected even while the active layer is still present`() {
        val after = document(1, 4, 2)
        val added = LayerStructureTransition.Added(layer(4, ""), 1)

        assertEquals(layerId(4), LayerSelectionPolicy.afterApplied(layerId(1), after, add(after), added))
        assertNull(LayerSelectionPolicy.afterApplied(layerId(1), after, undo(after), added))
    }

    @Test
    fun `a deleted active layer moves to the same position clamped to the top`() {
        val after = document(1, 3)

        val middle = LayerStructureTransition.Deleted(layer(2, "layer2"), 1)
        assertEquals(layerId(3), LayerSelectionPolicy.afterApplied(layerId(2), after, undo(after), middle))
        val top = LayerStructureTransition.Deleted(layer(4, "layer4"), 2)
        assertEquals(layerId(3), LayerSelectionPolicy.afterApplied(layerId(4), after, undo(after), top))
    }

    @Test
    fun `any other vanished active layer moves to the top layer`() {
        val after = document(1, 2, 3)

        assertEquals(layerId(3), LayerSelectionPolicy.afterApplied(layerId(9), after, undo(after), none))
    }

    @Test
    fun `a gesture target is lost when its layer is gone or hidden`() {
        val document =
            layeredState(listOf(layer(1, "a"), layer(2, "b", visibility = LayerVisibility.Hidden)))

        assertFalse(LayerSelectionPolicy.gestureTargetLost(layerId(1), document))
        assertTrue(LayerSelectionPolicy.gestureTargetLost(layerId(2), document))
        assertTrue(LayerSelectionPolicy.gestureTargetLost(layerId(3), document))
    }

    private fun document(vararg ids: Int): DocumentState = layeredState(ids.map { layer(it, "layer$it") })

    private fun undo(document: DocumentState): DocumentCommand = UndoCommand.create(document.id, document.revision)

    private fun add(document: DocumentState): DocumentCommand =
        AddLayerCommand.create(CommandGateway.create(document).captureSource(), layerId(1))
}
