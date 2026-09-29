package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.composited
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layerId
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.value
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StrokeCompositeTest {
    // 0: alpha 0 black, 1: partial red, 2: alpha 0 with RGB, 3: opaque, 4: alpha 1.
    private val colors = palette(0x00000000, 0xFF000080, 0x0A141E00, 0x102030FF, 0x7F7F7F01)
    private val size = canvas(4, 2)
    private val layers =
        listOf(
            layer(1, size, listOf(1, 2, null, 3, 4, null, 1, 2)),
            layer(2, size, listOf(2, null, 1, 4, null, 3, 1, null)),
            layer(3, size, listOf(3, 3, 3, 3, 3, 3, 3, 3), LayerVisibility.Hidden),
            layer(4, size, listOf(null, 1, 4, 2, 1, null, 0, 3)),
        )
    private val effects =
        listOf(StrokeEffect.Erase) + (0..4).map { index -> StrokeEffect.Paint(value(PaletteIndex.create(index))) }

    @Test
    fun `every pixel matches compositeLayers with the target layer replaced`() {
        listOf(1, 2, 4).forEach { targetId ->
            effects.forEach { effect ->
                val replaced =
                    layers.map { layer -> if (layer.id == layerId(targetId)) replaced(layer, effect) else layer }
                val expected = composited(compositeLayers(size, replaced, colors))

                val actual = allPixels(prepared(layers, StrokeCompositeTarget(layerId(targetId), effect)))

                assertArrayEquals(expected, actual, "target $targetId, effect $effect")
            }
        }
    }

    @Test
    fun `single layer paints palette RGBA and erases to zero`() {
        val single = listOf(layer(1, size, listOf(3, null, 1, 2, 4, 0, null, 3)))
        val entries = colors.palette.entries()

        effects.forEach { effect ->
            val expected =
                when (effect) {
                    StrokeEffect.Erase -> 0
                    is StrokeEffect.Paint -> entries[effect.targetIndex.value].color.toPackedRgba8888()
                }

            val actual = allPixels(prepared(single, StrokeCompositeTarget(layerId(1), effect)))

            assertArrayEquals(IntArray(size.pixelCount.toInt()) { expected }, actual, "effect $effect")
        }
    }

    @Test
    fun `composite validation is shared and covers hidden layers`() {
        val erase = StrokeCompositeTarget(layerId(1), StrokeEffect.Erase)
        val wide = canvas(8, 1)
        val wideHidden = layers + layer(5, wide, List(8) { 0 }, LayerVisibility.Hidden)
        val outsideHidden = layers + layer(5, size, List(8) { 5 }, LayerVisibility.Hidden)

        assertEquals(
            StrokeCompositeResult.Rejected(CompositeRejection.NoLayers),
            prepareStrokeComposite(size, emptyList(), colors, erase),
        )
        assertEquals(
            StrokeCompositeResult.Rejected(CompositeRejection.LayerSizeMismatch(layerId(5), size, wide)),
            prepareStrokeComposite(size, wideHidden, colors, erase),
        )
        assertEquals(
            StrokeCompositeResult.Rejected(
                CompositeRejection.IndexOutsidePalette(layerId(5), value(PaletteIndex.create(5))),
            ),
            prepareStrokeComposite(size, outsideHidden, colors, erase),
        )
    }

    @Test
    fun `missing or hidden target layer is not visible`() {
        listOf(3, 9).forEach { targetId ->
            val target = StrokeCompositeTarget(layerId(targetId), StrokeEffect.Erase)

            assertEquals(
                StrokeCompositeResult.TargetLayerNotVisible(layerId(targetId)),
                prepareStrokeComposite(size, layers, colors, target),
            )
        }
    }

    @Test
    fun `paint index outside palette is rejected`() {
        val outside = value(PaletteIndex.create(5))
        val target = StrokeCompositeTarget(layerId(1), StrokeEffect.Paint(outside))

        assertEquals(
            StrokeCompositeResult.TargetIndexOutsidePalette(outside),
            prepareStrokeComposite(size, layers, colors, target),
        )
    }

    @Test
    fun `checks run in contract order`() {
        val outsidePaint = StrokeEffect.Paint(value(PaletteIndex.create(5)))
        val missingTarget = StrokeCompositeTarget(layerId(9), outsidePaint)

        assertEquals(
            StrokeCompositeResult.Rejected(CompositeRejection.NoLayers),
            prepareStrokeComposite(size, emptyList(), colors, missingTarget),
        )
        assertEquals(
            StrokeCompositeResult.TargetLayerNotVisible(layerId(9)),
            prepareStrokeComposite(size, layers, colors, missingTarget),
        )
    }

    @Test
    fun `pixel outside the canvas is rejected`() {
        val composite = prepared(layers, StrokeCompositeTarget(layerId(1), StrokeEffect.Erase))

        assertEquals(size, composite.size)
        assertThrows<IllegalArgumentException> { composite.packedRgba8888At(-1) }
        assertThrows<IllegalArgumentException> { composite.packedRgba8888At(size.pixelCount.toInt()) }
    }

    private fun replaced(
        layer: Layer,
        effect: StrokeEffect,
    ): Layer =
        when (effect) {
            StrokeEffect.Erase -> layer.withSnapshot(PixelSnapshot.createEmpty(size))
            is StrokeEffect.Paint -> layer.withSnapshot(value(PixelSnapshot.createFilled(size, effect.targetIndex)))
        }

    private fun prepared(
        layers: List<Layer>,
        target: StrokeCompositeTarget,
    ): StrokeComposite =
        when (val result = prepareStrokeComposite(size, layers, colors, target)) {
            is StrokeCompositeResult.Prepared -> result.composite
            else -> error("Unexpected stroke composite result: $result")
        }

    private fun allPixels(composite: StrokeComposite): IntArray =
        IntArray(composite.size.pixelCount.toInt()) { pixel -> composite.packedRgba8888At(pixel) }
}
