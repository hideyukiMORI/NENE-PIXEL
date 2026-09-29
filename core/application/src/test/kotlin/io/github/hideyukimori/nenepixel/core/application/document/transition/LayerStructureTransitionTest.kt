package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.assertFullCanvasInvalidation
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerName
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.roundTrip
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.structural
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.threeLayerState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class LayerStructureTransitionTest {
    @Test
    fun `added layer is inserted at its after position and the inverse removes it`() {
        val original = threeLayerState()
        val added = layer(4, "レイヤー")
        val changeSet = structural(original, LayerStructureTransition.Added(added, 1))

        val next = roundTrip(original, changeSet)

        assertEquals(listOf(1, 4, 2, 3), next.layers.map { it.id.value })
        assertEquals(added, next.layers[1])
        assertEquals(12L, changeSet.structure.retainedByteCount)
        assertEquals(TRANSITION_BYTES + 12L, changeSet.retainedByteCount)
        assertEquals(0, changeSet.retainedChangeCount)
        assertFullCanvasInvalidation(changeSet)
    }

    @Test
    fun `deleted layer is removed from its before position and the inverse restores it`() {
        val original = threeLayerState()
        val deleted = original.layers[1]
        val changeSet = structural(original, LayerStructureTransition.Deleted(deleted, 1))

        val next = roundTrip(original, changeSet)

        assertEquals(listOf(1, 3), next.layers.map { it.id.value })
        // 2 pixels + ceil(2 / 8) coverage byte + "middle" (6 UTF-8 bytes).
        assertEquals(9L, changeSet.structure.retainedByteCount)
        assertEquals(TRANSITION_BYTES + 9L, changeSet.retainedByteCount)
        assertFullCanvasInvalidation(changeSet)
    }

    @Test
    fun `renamed layer takes the after name and the inverse restores the before name`() {
        val original = threeLayerState()
        val changeSet =
            structural(
                original,
                LayerStructureTransition.Renamed(layerId(2), layerName("middle"), layerName("線画")),
            )

        val next = roundTrip(original, changeSet)

        assertEquals(layerName("線画"), next.layers[1].name)
        assertEquals(6L + 6L, changeSet.structure.retainedByteCount)
        assertEquals(TRANSITION_BYTES + 12L, changeSet.retainedByteCount)
        assertFullCanvasInvalidation(changeSet)
    }

    @Test
    fun `moved layer lands at its to position and the inverse moves it back`() {
        val original = threeLayerState()
        val changeSet = structural(original, LayerStructureTransition.Moved(layerId(1), 0, 2))

        val next = roundTrip(original, changeSet)

        assertEquals(listOf(2, 3, 1), next.layers.map { it.id.value })
        assertEquals(0L, changeSet.structure.retainedByteCount)
        assertEquals(TRANSITION_BYTES, changeSet.retainedByteCount)
        assertFullCanvasInvalidation(changeSet)
    }

    @Test
    fun `visibility change sets the after visibility and the inverse restores it`() {
        val original = threeLayerState()
        val changeSet =
            structural(
                original,
                LayerStructureTransition.VisibilityChanged(layerId(3), LayerVisibility.Visible, LayerVisibility.Hidden),
            )

        val next = roundTrip(original, changeSet)

        assertEquals(LayerVisibility.Hidden, next.layers[2].visibility)
        assertEquals(0L, changeSet.structure.retainedByteCount)
        assertEquals(TRANSITION_BYTES, changeSet.retainedByteCount)
        assertFullCanvasInvalidation(changeSet)
    }

    @Test
    fun `inverse swaps each structure transition with its counterpart`() {
        val added = layer(4, "a")
        val before = layerName("a")
        val after = layerName("b")

        assertEquals(LayerStructureTransition.None, LayerStructureTransition.None.inverse())
        assertEquals(LayerStructureTransition.Deleted(added, 2), LayerStructureTransition.Added(added, 2).inverse())
        assertEquals(LayerStructureTransition.Added(added, 2), LayerStructureTransition.Deleted(added, 2).inverse())
        assertEquals(
            LayerStructureTransition.Renamed(layerId(1), after, before),
            LayerStructureTransition.Renamed(layerId(1), before, after).inverse(),
        )
        assertEquals(
            LayerStructureTransition.Moved(layerId(1), 3, 0),
            LayerStructureTransition.Moved(layerId(1), 0, 3).inverse(),
        )
        val shown = LayerVisibility.Visible
        val hidden = LayerVisibility.Hidden
        assertEquals(
            LayerStructureTransition.VisibilityChanged(layerId(1), hidden, shown),
            LayerStructureTransition.VisibilityChanged(layerId(1), shown, hidden).inverse(),
        )
    }

    @Test
    fun `change set inverse carries the inverted structure and swapped revisions`() {
        val original = threeLayerState()
        val changeSet = structural(original, LayerStructureTransition.Moved(layerId(1), 0, 2))

        val inverse = changeSet.inverse()

        assertEquals(LayerStructureTransition.Moved(layerId(1), 2, 0), inverse.structure)
        assertEquals(changeSet.afterRevision, inverse.beforeRevision)
        assertEquals(changeSet.beforeRevision, inverse.afterRevision)
        assertEquals(changeSet, inverse.inverse())
    }

    @Test
    fun `deleting a 256 by 256 layer retains its pixels coverage bits and name bytes`() {
        val size = canvas(256, 256)
        val deleted = layer(1, "背景", PixelSnapshot.createEmpty(size))

        val structure = LayerStructureTransition.Deleted(deleted, 0)

        assertEquals(73_728L + 6L, structure.retainedByteCount)
    }

    @Test
    fun `adding a layer to a document at the layer maximum is rejected by the layered document factory`() {
        val full = layeredState(List(LayerLimits.MAX_LAYERS) { index -> layer(index + 1, "L$index") })
        val changeSet =
            structural(full, LayerStructureTransition.Added(layer(LayerLimits.MAX_LAYERS + 1, "extra"), 0))

        val reason = rejected(DocumentTransition.create(full, changeSet))

        assertInstanceOf(RejectionReason.InvalidIndexedValue::class.java, reason)
    }

    @Test
    fun `pixel change sets keep no structure transition`() {
        val original = threeLayerState()
        val changeSet = ChangeSet.create(original, original.revision, PaletteTransition.Unchanged, emptyList())

        assertEquals(LayerStructureTransition.None, changeSet.structure)
        assertEquals(TRANSITION_BYTES, changeSet.retainedByteCount)
    }

    private companion object {
        const val TRANSITION_BYTES: Long = 32L
    }
}
