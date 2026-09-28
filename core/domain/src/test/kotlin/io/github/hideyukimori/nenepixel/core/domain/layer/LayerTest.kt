package io.github.hideyukimori.nenepixel.core.domain.layer

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class LayerTest {
    @Test
    fun `create keeps all four fields`() {
        val layer = layer()

        assertEquals(LayerId.first(), layer.id)
        assertEquals(created(LayerName.create("base")), layer.name)
        assertEquals(LayerVisibility.Visible, layer.visibility)
        assertEquals(snapshot(0), layer.snapshot)
    }

    @Test
    fun `with functions replace exactly one field`() {
        val original = layer()
        val renamed = original.withName(LayerName.empty)
        val hidden = original.withVisibility(LayerVisibility.Hidden)
        val repainted = original.withSnapshot(snapshot(1))

        assertEquals(LayerName.empty, renamed.name)
        assertEquals(LayerVisibility.Hidden, hidden.visibility)
        assertEquals(snapshot(1), repainted.snapshot)
        assertEquals(original, renamed.withName(original.name))
        assertEquals(original, hidden.withVisibility(LayerVisibility.Visible))
        assertEquals(original, repainted.withSnapshot(snapshot(0)))
    }

    @Test
    fun `equality covers all four fields`() {
        val original = layer()

        assertEquals(original, layer())
        assertEquals(original.hashCode(), layer().hashCode())
        assertNotEquals(
            original,
            Layer.create(created(LayerId.create(2)), original.name, original.visibility, original.snapshot),
        )
        assertNotEquals(original, original.withName(LayerName.empty))
        assertNotEquals(original, original.withVisibility(LayerVisibility.Hidden))
        assertNotEquals(original, original.withSnapshot(snapshot(1)))
    }

    private fun layer(): Layer =
        Layer.create(LayerId.first(), created(LayerName.create("base")), LayerVisibility.Visible, snapshot(0))

    private fun snapshot(index: Int): PixelSnapshot =
        created(
            PixelSnapshot.create(
                canvasSize(1, 1),
                Revision.initial(),
                listOf(created(PaletteIndex.create(index))),
            ),
        )
}
