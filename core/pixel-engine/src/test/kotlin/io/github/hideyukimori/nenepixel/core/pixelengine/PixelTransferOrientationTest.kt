package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
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

class PixelTransferOrientationTest {
    @Test
    fun `eight fixed non-square oracles preserve Empty zero and255 through overlap and inverse`() {
        val size = canvas(6, 6)
        val source = value(PixelRegion.create(size, PixelTransferCells.position(1, 1), canvas(3, 2)))
        val original = List<Int?>(36) { it + 6 }.toMutableList()
        place(original, source, listOf(null, 0, 255, 3, 4, 5), 6)
        val snapshot = layer(1, size, original).snapshot
        PixelOrientation.entries.forEach { orientation ->
            PixelTransferMode.entries.forEach { mode ->
                listOf(1 to 1, 2 to 1, 0 to 2, 3 to 3).forEach { (x, y) ->
                    val placement = PixelPlacement.create(PixelTransferCells.position(x, y), orientation)
                    val transfer = value(PixelTransfer.create(size, source, placement, mode))
                    verifyTransfer(snapshot, original, transfer)
                }
            }
        }
    }

    @Test
    fun `equal origin transformed cells are evaluated while symmetric and Empty contents remain no-op`() {
        val size = canvas(3, 2)
        val source = value(PixelRegion.create(size, PixelTransferCells.position(0, 0), size))
        val flip =
            value(
                PixelTransfer.create(
                    size,
                    source,
                    PixelPlacement.create(source.origin, PixelOrientation.FlipHorizontal),
                    PixelTransferMode.Move,
                ),
            )
        val distinct = layer(1, size, listOf(0, 1, 2, 3, 4, 5)).snapshot
        assertInstanceOf(
            PixelTransferRasterizationResult.Rasterized::class.java,
            rasterizePixelTransfer(distinct, flip),
        )
        assertSame(
            PixelTransferRasterizationResult.NoChanges,
            rasterizePixelTransfer(
                layer(
                    1,
                    size,
                    List(6) {
                        0
                    },
                ).snapshot,
                flip,
            ),
        )
        assertSame(
            PixelTransferRasterizationResult.NoChanges,
            rasterizePixelTransfer(PixelSnapshot.createEmpty(size), flip),
        )
        val identity =
            value(PixelTransfer.create(size, source, PixelPlacement.create(source.origin), PixelTransferMode.Move))
        assertSame(PixelTransferRasterizationResult.NoChanges, rasterizePixelTransfer(distinct, identity))
    }

    private fun verifyTransfer(
        snapshot: PixelSnapshot,
        original: List<Int?>,
        transfer: PixelTransfer,
    ) {
        val expected = original.toMutableList()
        val stride = transfer.canvas.width.value
        if (transfer.mode == PixelTransferMode.Move) place(expected, transfer.source, List(6) { null }, stride)
        place(expected, transfer.destinationRegion, oracle(transfer.orientation), stride)
        val result = rasterizePixelTransfer(snapshot, transfer)
        val actual = snapshotAfter(snapshot, result)
        assertEquals(
            layer(1, transfer.canvas, expected).snapshot,
            actual,
            "${transfer.orientation} ${transfer.mode} at ${transfer.destination}",
        )
        if (result is PixelTransferRasterizationResult.Rasterized) {
            assertEquals(snapshot, applied(result.patch.inverse(), actual))
            val again = rasterizePixelTransfer(snapshot, transfer) as PixelTransferRasterizationResult.Rasterized
            assertEquals(actual, applied(again.patch, snapshot))
        }
        assertEquals(layer(1, transfer.canvas, original).snapshot, snapshot)
    }

    // Literal rows for original [Empty,0,255] / [3,4,5], independent of the engine inverse rule.
    private fun oracle(orientation: PixelOrientation): List<Int?> =
        when (orientation) {
            PixelOrientation.Identity -> listOf(null, 0, 255, 3, 4, 5)
            PixelOrientation.RotateClockwise -> listOf(3, null, 4, 0, 5, 255)
            PixelOrientation.HalfTurn -> listOf(5, 4, 3, 255, 0, null)
            PixelOrientation.RotateCounterClockwise -> listOf(255, 5, 0, 4, null, 3)
            PixelOrientation.FlipHorizontal -> listOf(255, 0, null, 5, 4, 3)
            PixelOrientation.FlipVertical -> listOf(3, 4, 5, null, 0, 255)
            PixelOrientation.Transpose -> listOf(null, 3, 0, 4, 255, 5)
            PixelOrientation.Transverse -> listOf(5, 255, 4, 0, 3, null)
        }

    private fun place(
        destination: MutableList<Int?>,
        region: PixelRegion,
        cells: List<Int?>,
        stride: Int,
    ) {
        cells.forEachIndexed { index, cell ->
            val x = region.origin.x.value + index % region.size.width.value
            val y = region.origin.y.value + index / region.size.width.value
            destination[y * stride + x] = cell
        }
    }

    private fun snapshotAfter(
        before: PixelSnapshot,
        result: PixelTransferRasterizationResult,
    ): PixelSnapshot =
        when (result) {
            PixelTransferRasterizationResult.NoChanges -> before
            is PixelTransferRasterizationResult.Rasterized -> applied(result.patch, before)
            is PixelTransferRasterizationResult.Rejected -> error("Unexpected rejection: ${result.rejection}")
        }

    private fun applied(
        patch: PixelPatch,
        snapshot: PixelSnapshot,
    ): PixelSnapshot =
        assertInstanceOf(PixelPatchApplicationResult.Applied::class.java, patch.applyTo(snapshot)).snapshot
}
