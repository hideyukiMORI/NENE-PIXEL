package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class DocumentStateLayeredTest {
    @Test
    fun `layered factory accepts one layer and keeps document revision`() {
        val layer = layer(1, snapshot(2, 1, listOf(0, 1)))
        val state = created(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, listOf(layer)))

        assertEquals(DOCUMENT_ID, state.id)
        assertEquals(REVISION, state.revision)
        assertEquals(DEFINITION, state.definition)
        assertEquals(listOf(layer), state.layers)
        assertEquals(canvasSize(2, 1), state.size)
    }

    @Test
    fun `layered factory accepts the maximum layer count in bottom to top order`() {
        val layers = (1..LayerLimits.MAX_LAYERS).map { layer(it, snapshot(2, 1, listOf(0, 0))) }
        val state = created(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))

        assertEquals(layers, state.layers)
        val top = state.layers.last()
        assertEquals(LayerLimits.MAX_LAYERS, top.id.value)
    }

    @Test
    fun `layered factory rejects zero layers`() {
        val rejection = rejected(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, emptyList()))

        assertEquals(DomainValueRejection.DocumentLayerCountOutOfRange(0), rejection)
    }

    @Test
    fun `layered factory rejects more than the maximum layer count`() {
        val layers = (1..LayerLimits.MAX_LAYERS + 1).map { layer(it, snapshot(2, 1, listOf(0, 0))) }
        val rejection = rejected(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))

        assertEquals(DomainValueRejection.DocumentLayerCountOutOfRange(LayerLimits.MAX_LAYERS + 1), rejection)
    }

    @Test
    fun `layered factory rejects the first duplicated layer id`() {
        val pixels = snapshot(2, 1, listOf(0, 0))
        val layers = listOf(layer(1, pixels), layer(2, pixels), layer(3, pixels), layer(2, pixels), layer(1, pixels))
        val rejection = rejected(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))

        assertEquals(DomainValueRejection.DuplicateLayerId(2), rejection)
    }

    @Test
    fun `layered factory rejects a layer whose size differs from the bottom layer`() {
        val layers = listOf(layer(1, snapshot(2, 1, listOf(0, 0))), layer(2, snapshot(1, 2, listOf(0, 0))))
        val rejection = rejected(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))

        val mismatch = assertInstanceOf(DomainValueRejection.LayerSizeMismatch::class.java, rejection)
        assertEquals(2, mismatch.layerId)
        assertEquals(canvasSize(2, 1), mismatch.expected)
        assertEquals(canvasSize(1, 2), mismatch.actual)
    }

    @Test
    fun `layered factory rejects a palette index outside the definition in any layer`() {
        val layers = listOf(layer(1, snapshot(2, 1, listOf(0, 1))), layer(2, snapshot(2, 1, listOf(0, 3))))
        val rejection = rejected(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))

        val outside = assertInstanceOf(DomainValueRejection.PaletteIndexOutsidePalette::class.java, rejection)
        assertEquals(index(3), outside.attemptedIndex)
        assertEquals(PALETTE_SIZE, outside.entryCount)
    }

    @Test
    fun `document keeps its own copy of the caller layer list`() {
        val bottom = layer(1, snapshot(2, 1, listOf(0, 0)))
        val callerLayers = mutableListOf(bottom)
        val state = created(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, callerLayers))

        callerLayers.add(layer(2, snapshot(2, 1, listOf(1, 1))))
        callerLayers[0] = layer(3, snapshot(2, 1, listOf(2, 2)))

        assertEquals(listOf(bottom), state.layers)
    }

    @Test
    fun `migration factory builds one visible unnamed first layer with the snapshot revision`() {
        val pixels = snapshot(2, 1, listOf(0, 2), created(Revision.create(7)))
        val state = created(DocumentState.create(DOCUMENT_ID, DEFINITION, pixels))

        assertEquals(1, state.layers.size)
        val only = state.layers.single()
        assertEquals(LayerId.first(), only.id)
        assertEquals(LayerName.empty, only.name)
        assertEquals(LayerVisibility.Visible, only.visibility)
        assertEquals(pixels, only.snapshot)
        assertEquals(pixels.revision, state.revision)
    }

    @Test
    fun `migration snapshot reads the bottom layer`() {
        val bottom = snapshot(2, 1, listOf(1, 1))
        val layers = listOf(layer(1, bottom), layer(2, snapshot(2, 1, listOf(2, 2))))
        val state = created(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))

        assertSame(bottom, state.snapshot)
    }

    @Test
    fun `equality distinguishes document revision and layers`() {
        val layers = listOf(layer(1, snapshot(2, 1, listOf(0, 0))))
        val state = created(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers))
        val same = created(DocumentState.createLayered(DOCUMENT_ID, REVISION, DEFINITION, layers.toList()))
        val otherRevision = created(DocumentState.createLayered(DOCUMENT_ID, Revision.initial(), DEFINITION, layers))
        val otherLayers =
            created(
                DocumentState.createLayered(
                    DOCUMENT_ID,
                    REVISION,
                    DEFINITION,
                    listOf(layers.single().withVisibility(LayerVisibility.Hidden)),
                ),
            )

        assertEquals(state, same)
        assertEquals(state.hashCode(), same.hashCode())
        assertNotEquals(state, otherRevision)
        assertNotEquals(state, otherLayers)
    }

    private fun layer(
        id: Int,
        pixels: PixelSnapshot,
    ): Layer = Layer.create(created(LayerId.create(id)), LayerName.empty, LayerVisibility.Visible, pixels)

    private fun snapshot(
        width: Int,
        height: Int,
        indices: List<Int>,
        revision: Revision = Revision.initial(),
    ): PixelSnapshot = created(PixelSnapshot.create(canvasSize(width, height), revision, indices.map(::index)))

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))

    private companion object {
        const val PALETTE_SIZE = 3
        val DOCUMENT_ID = created(DocumentId.create("0".repeat(32)))
        val REVISION = created(Revision.create(5))
        val DEFINITION: PaletteDefinition =
            created(
                PaletteDefinition.create(
                    created(Palette.create(List(PALETTE_SIZE) { color(it, it, it, 255) })),
                    PaletteIndex.first,
                ),
            )
    }
}
