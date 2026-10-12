package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.layer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeTestValues.value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PixelTransferRasterizationTest {
    @Test
    fun `all overlap directions read the original rectangle and inverse restores exact cells`() {
        val size = canvas(5, 5)
        // Every covered cell has a unique index, so all eight non-zero displacements are effective.
        val original = List(25) { if (it % 5 == 0) null else it }
        val snapshot = layer(1, size, original).snapshot
        val source = value(PixelRegion.create(size, PixelTransferCells.position(1, 1), canvas(3, 3)))
        val destinations = listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2)
        PixelTransferMode.entries.forEach { mode ->
            destinations.forEach { (x, y) ->
                val transfer =
                    value(
                        PixelTransfer.create(
                            size,
                            source,
                            PixelPlacement.create(PixelTransferCells.position(x, y)),
                            mode,
                        ),
                    )
                val patch = rasterized(snapshot, transfer)
                val result = applied(patch, snapshot)
                assertEquals(layer(1, size, expected(original, transfer)).snapshot, result)
                assertEquals(snapshot, applied(patch.inverse(), result))
                assertEquals(layer(1, size, original).snapshot, snapshot)
            }
        }
    }

    @Test
    fun `Empty and covered zero and unsigned slot255 are distinct exact replacements`() {
        val size = canvas(6, 1)
        val snapshot = layer(1, size, listOf(null, 0, 255, 1, 2, 3)).snapshot
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(3, 1)))
        val copy =
            value(
                PixelTransfer.create(
                    size,
                    source,
                    PixelPlacement.create(PixelTransferCells.position(3, 0)),
                    PixelTransferMode.Copy,
                ),
            )
        assertEquals(
            layer(1, size, listOf(null, 0, 255, null, 0, 255)).snapshot,
            applied(rasterized(snapshot, copy), snapshot),
        )
    }

    @Test
    fun `zero offset equal cells and empty source union produce no patch`() {
        val size = canvas(3, 1)
        val snapshot = layer(1, size, listOf(0, 0, 0)).snapshot
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(1, 1)))
        val same =
            value(
                PixelTransfer.create(
                    size,
                    source,
                    PixelPlacement.create(PixelTransferCells.position(0, 0)),
                    PixelTransferMode.Move,
                ),
            )
        val copy =
            value(
                PixelTransfer.create(
                    size,
                    source,
                    PixelPlacement.create(PixelTransferCells.position(2, 0)),
                    PixelTransferMode.Copy,
                ),
            )
        assertSame(PixelTransferRasterizationResult.NoChanges, rasterizePixelTransfer(snapshot, same))
        assertSame(PixelTransferRasterizationResult.NoChanges, rasterizePixelTransfer(snapshot, copy))
        assertSame(
            PixelTransferRasterizationResult.NoChanges,
            rasterizePixelTransfer(PixelSnapshot.createEmpty(size), copy),
        )
    }

    @Test
    fun `distant single cells visit only two sorted positions and wrong canvas rejects`() {
        val size = canvas(256, 256)
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(1, 1)))
        val transfer =
            value(
                PixelTransfer.create(
                    size,
                    source,
                    PixelPlacement.create(PixelTransferCells.position(255, 255)),
                    PixelTransferMode.Move,
                ),
            )
        val visited = mutableListOf<Int>()
        PixelTransferPositions(transfer).forEachIndex(visited::add)
        assertEquals(listOf(0, 65_535), visited)
        val rejected =
            assertInstanceOf(
                PixelTransferRasterizationResult.Rejected::class.java,
                rasterizePixelTransfer(PixelSnapshot.createEmpty(canvas(2, 2)), transfer),
            )
        assertEquals(size, (rejected.rejection as PixelTransferRasterizationRejection.CanvasMismatch).expected)
    }

    @Test
    fun `disjoint half-canvas move reaches exactly maximum patch count without a second mutation`() {
        val size = canvas(256, 256)
        val original = List(65_536) { if (it % 256 < 128) 0 else 1 }
        val snapshot = layer(1, size, original).snapshot
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), canvas(128, 256)))
        val transfer =
            value(
                PixelTransfer.create(
                    size,
                    source,
                    PixelPlacement.create(PixelTransferCells.position(128, 0)),
                    PixelTransferMode.Move,
                ),
            )
        val patch = rasterized(snapshot, transfer)
        assertEquals(65_536, patch.changeCount)
        val moved = applied(patch, snapshot)
        assertEquals(snapshot, applied(patch.inverse(), moved))
    }

    private fun rasterized(
        snapshot: PixelSnapshot,
        transfer: PixelTransfer,
    ): PixelPatch =
        assertInstanceOf(
            PixelTransferRasterizationResult.Rasterized::class.java,
            rasterizePixelTransfer(snapshot, transfer),
        ).patch

    private fun applied(
        patch: PixelPatch,
        snapshot: PixelSnapshot,
    ): PixelSnapshot =
        assertInstanceOf(PixelPatchApplicationResult.Applied::class.java, patch.applyTo(snapshot)).snapshot

    private fun expected(
        original: List<Int?>,
        transfer: PixelTransfer,
    ): List<Int?> {
        val result = original.toMutableList()
        val source = transfer.source
        if (transfer.mode == PixelTransferMode.Move) visit(source, transfer.canvas) { result[it] = null }
        repeat(source.size.height.value) { row ->
            repeat(source.size.width.value) { column ->
                val old = (source.origin.y.value + row) * transfer.canvas.width.value + source.origin.x.value + column
                val destination =
                    (transfer.destination.y.value + row) * transfer.canvas.width.value + transfer.destination.x.value +
                        column
                result[destination] = original[old]
            }
        }
        return result
    }

    private fun visit(
        region: PixelRegion,
        size: CanvasSize,
        consume: (Int) -> Unit,
    ) {
        repeat(region.size.height.value) { row ->
            repeat(region.size.width.value) { column ->
                consume((region.origin.y.value + row) * size.width.value + region.origin.x.value + column)
            }
        }
    }
}
