package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferCells
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.composited
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layerId
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.value
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizePixelTransfer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PixelTransferCompositeTest {
    @Test
    fun `prepared Move Copy and offset changes equal the real patch composite`() {
        val size = canvas(4, 3)
        val colors = palette(0xAABBCC00, 0xFF000080, 0x0000FFFF, 0x00FF0040)
        val layers =
            listOf(
                layer(1, size, List(12) { 3 }),
                layer(2, size, listOf(0, 1, null, 2, 2, null, 1, 0, null, 3, 0, 1)),
                layer(3, size, listOf(null, 3, 2, null, 0, 1, null, 3, 2, null, 1, 0)),
                layer(4, size, List(12) { 2 }, LayerVisibility.Hidden),
            )
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(2, 2)))
        val prepared =
            assertInstanceOf(
                PixelTransferCompositeResult.Prepared::class.java,
                preparePixelTransferComposite(size, layers, colors, PixelTransferCompositeTarget(layerId(2), source)),
            ).composite
        PixelTransferMode.entries.forEach { mode ->
            listOf(1 to 0, 2 to 1, 0 to 1).forEach { (x, y) ->
                val placement = PixelPlacement.create(PixelTransferCells.position(x, y))
                val transfer = value(PixelTransfer.create(size, source, placement, mode))
                val patch =
                    (
                        rasterizePixelTransfer(
                            layers[1].snapshot,
                            transfer,
                        ) as PixelTransferRasterizationResult.Rasterized
                    ).patch
                val target = (patch.applyTo(layers[1].snapshot) as PixelPatchApplicationResult.Applied).snapshot
                val changed = layers.toMutableList().also { it[1] = layers[1].withSnapshot(target) }
                val expected = composited(compositeLayers(size, changed, colors))
                prepared.forEachPosition(transfer) { position ->
                    assertEquals(
                        expected[position.y.value * 4 + position.x.value],
                        prepared.packedRgba8888At(position, transfer),
                    )
                }
            }
        }
    }

    @Test
    fun `one prepared non-square crop evaluates all orientations with the canonical layered composite`() {
        val size = canvas(5, 4)
        val colors = palette(0xAABBCC00, 0xFF000080, 0x0000FFFF, 0x00FF0040)
        val layers =
            listOf(
                layer(1, size, List(20) { 3 }),
                layer(2, size, List(20) { if (it % 5 == 0) null else it % 4 }),
                layer(3, size, List(20) { if (it % 3 == 0) null else (it + 1) % 4 }),
                layer(4, size, List(20) { 2 }, LayerVisibility.Hidden),
            )
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(3, 2)))
        val prepared =
            assertInstanceOf(
                PixelTransferCompositeResult.Prepared::class.java,
                preparePixelTransferComposite(size, layers, colors, PixelTransferCompositeTarget(layerId(2), source)),
            ).composite
        PixelOrientation.entries.forEach { orientation ->
            PixelTransferMode.entries.forEach { mode ->
                val placement = PixelPlacement.create(PixelTransferCells.position(2, 1), orientation)
                val transfer = value(PixelTransfer.create(size, source, placement, mode))
                val result = rasterizePixelTransfer(layers[1].snapshot, transfer)
                val patch = assertInstanceOf(PixelTransferRasterizationResult.Rasterized::class.java, result).patch
                val target = (patch.applyTo(layers[1].snapshot) as PixelPatchApplicationResult.Applied).snapshot
                val changed = layers.toMutableList().also { it[1] = layers[1].withSnapshot(target) }
                val expected = composited(compositeLayers(size, changed, colors))
                prepared.forEachPosition(transfer) { position ->
                    assertEquals(
                        expected[position.y.value * 5 + position.x.value],
                        prepared.packedRgba8888At(position, transfer),
                        "$orientation $mode at $position",
                    )
                }
            }
        }
    }

    @Test
    fun `target visibility and source geometry are guarded before copying`() {
        val size = canvas(2, 2)
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(1, 1)))
        val colors = palette(0, 0xFFFFFFFF)
        val hidden = listOf(layer(1, size, List(4) { 0 }, LayerVisibility.Hidden))
        assertInstanceOf(
            PixelTransferCompositeResult.TargetLayerNotVisible::class.java,
            preparePixelTransferComposite(size, hidden, colors, PixelTransferCompositeTarget(layerId(1), source)),
        )
        val visible = listOf(layer(1, size, List(4) { 0 }))
        val outside = value(PixelRegion.create(canvas(4, 4), PixelTransferCells.position(3, 3), canvas(1, 1)))
        assertInstanceOf(
            PixelTransferCompositeResult.SourceRegionRejected::class.java,
            preparePixelTransferComposite(size, visible, colors, PixelTransferCompositeTarget(layerId(1), outside)),
        )
        val prepared =
            (
                preparePixelTransferComposite(
                    size,
                    visible,
                    colors,
                    PixelTransferCompositeTarget(layerId(1), source),
                ) as PixelTransferCompositeResult.Prepared
            ).composite
        val changedSource = value(PixelRegion.create(size, PixelTransferCells.position(1, 1), canvas(1, 1)))
        val transfer =
            value(
                PixelTransfer.create(
                    size,
                    changedSource,
                    PixelPlacement.create(PixelTransferCells.position(0, 0)),
                    PixelTransferMode.Copy,
                ),
            )
        assertThrows(IllegalArgumentException::class.java) { prepared.forEachPosition(transfer) {} }
    }
}
