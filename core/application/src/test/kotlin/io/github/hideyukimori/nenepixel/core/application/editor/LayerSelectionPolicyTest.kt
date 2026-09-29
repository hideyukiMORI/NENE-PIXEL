package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerName
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

        assertNull(LayerSelectionPolicy.afterApplied(layerId(1), document, none))
        assertNull(LayerSelectionPolicy.afterApplied(layerId(2), document, none))
    }

    @Test
    fun `an inserted layer is selected even while the active layer is still present`() {
        val after = document(1, 4, 2)
        val added = LayerStructureTransition.Added(layer(4, ""), 1)
        val undoneDelete = LayerStructureTransition.Deleted(layer(4, "layer4"), 1).inverse()

        assertEquals(layerId(4), LayerSelectionPolicy.afterApplied(layerId(1), after, added))
        // A redo applies the recorded add again, so it arrives as the same insertion.
        assertEquals(layerId(4), LayerSelectionPolicy.afterApplied(layerId(2), after, added))
        assertEquals(layerId(4), LayerSelectionPolicy.afterApplied(layerId(1), after, undoneDelete))
    }

    @Test
    fun `a change that keeps the active layer leaves the selection unchanged`() {
        val after = document(1, 2, 3)

        val renamed = LayerStructureTransition.Renamed(layerId(2), layerName("a"), layerName("layer2"))
        assertNull(LayerSelectionPolicy.afterApplied(layerId(2), after, renamed))
        val moved = LayerStructureTransition.Moved(layerId(2), 0, 1)
        assertNull(LayerSelectionPolicy.afterApplied(layerId(2), after, moved))
        val hidden =
            LayerStructureTransition.VisibilityChanged(layerId(2), LayerVisibility.Visible, LayerVisibility.Hidden)
        assertNull(LayerSelectionPolicy.afterApplied(layerId(2), after, hidden))
    }

    @Test
    fun `a deleted active layer moves to the same position clamped to the top`() {
        val after = document(1, 3)

        val middle = LayerStructureTransition.Deleted(layer(2, "layer2"), 1)
        assertEquals(layerId(3), LayerSelectionPolicy.afterApplied(layerId(2), after, middle))
        val top = LayerStructureTransition.Deleted(layer(4, "layer4"), 2)
        assertEquals(layerId(3), LayerSelectionPolicy.afterApplied(layerId(4), after, top))
    }

    @Test
    fun `any other vanished active layer moves to the top layer`() {
        val after = document(1, 2, 3)

        assertEquals(layerId(3), LayerSelectionPolicy.afterApplied(layerId(9), after, none))
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
}
