package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerName
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.structural
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.threeLayerState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LayerStructureRejectionTest {
    @Test
    fun `adding a layer whose id is already in the document is a structure mismatch`() {
        val duplicate = layer(2, "copy")

        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(2)),
            reasonFor(LayerStructureTransition.Added(duplicate, 0)),
        )
    }

    @Test
    fun `adding a layer outside zero through the layer count is a structure mismatch`() {
        val added = layer(4, "new")

        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(4)),
            reasonFor(LayerStructureTransition.Added(added, 4)),
        )
        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(4)),
            reasonFor(LayerStructureTransition.Added(added, -1)),
        )
    }

    @Test
    fun `deleting a layer that differs from the recorded one or its position is a structure mismatch`() {
        val current = threeLayerState()
        val renamed = current.layers[1].withName(layerName("other"))

        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(2)),
            reasonFor(LayerStructureTransition.Deleted(renamed, 1)),
        )
        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(2)),
            reasonFor(LayerStructureTransition.Deleted(current.layers[1], 0)),
        )
    }

    @Test
    fun `deleting a layer absent from the document is layer not found`() {
        assertEquals(
            RejectionReason.LayerNotFound(layerId(9)),
            reasonFor(LayerStructureTransition.Deleted(layer(9, "gone"), 0)),
        )
    }

    @Test
    fun `renaming from a name the layer does not have is a structure mismatch`() {
        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(1)),
            reasonFor(LayerStructureTransition.Renamed(layerId(1), layerName("middle"), layerName("x"))),
        )
        assertEquals(
            RejectionReason.LayerNotFound(layerId(9)),
            reasonFor(LayerStructureTransition.Renamed(layerId(9), layerName("bottom"), layerName("x"))),
        )
    }

    @Test
    fun `moving from a position the layer is not at or to one outside the list is a structure mismatch`() {
        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(1)),
            reasonFor(LayerStructureTransition.Moved(layerId(1), 1, 2)),
        )
        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(1)),
            reasonFor(LayerStructureTransition.Moved(layerId(1), 0, 3)),
        )
        assertEquals(
            RejectionReason.LayerNotFound(layerId(9)),
            reasonFor(LayerStructureTransition.Moved(layerId(9), 0, 1)),
        )
    }

    @Test
    fun `changing visibility from a state the layer is not in is a structure mismatch`() {
        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(3)),
            reasonFor(
                LayerStructureTransition.VisibilityChanged(layerId(3), LayerVisibility.Hidden, LayerVisibility.Visible),
            ),
        )
    }

    @Test
    fun `replaying a structure change on its own result is rejected`() {
        val original = threeLayerState()
        val changeSet = structural(original, LayerStructureTransition.Moved(layerId(1), 0, 2))
        val next = DocumentTransitionAssertions.created(DocumentTransition.create(original, changeSet)).nextState
        val replay = structural(next, LayerStructureTransition.Moved(layerId(1), 0, 2))

        assertEquals(
            RejectionReason.LayerStructureMismatch(layerId(1)),
            rejected(DocumentTransition.create(next, replay)),
        )
    }

    private fun reasonFor(structure: LayerStructureTransition): RejectionReason {
        val current = threeLayerState()
        return rejected(DocumentTransition.create(current, structural(current, structure)))
    }
}
