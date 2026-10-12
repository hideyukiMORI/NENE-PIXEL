package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Plans only S union D (D for Copy), reading one immutable source and publishing an ordinary patch. */
public fun rasterizePixelTransfer(
    snapshot: PixelSnapshot,
    transfer: PixelTransfer,
): PixelTransferRasterizationResult =
    when {
        snapshot.size != transfer.canvas -> {
            PixelTransferRasterizationResult.Rejected(
                PixelTransferRasterizationRejection.CanvasMismatch(transfer.canvas, snapshot.size),
            )
        }

        transfer.source.origin == transfer.destination -> {
            PixelTransferRasterizationResult.NoChanges
        }

        else -> {
            PixelTransferPatchPlanner(snapshot, transfer).plan()
        }
    }

private class PixelTransferPatchPlanner(
    private val snapshot: PixelSnapshot,
    private val transfer: PixelTransfer,
) {
    private val cells = PixelTransferCells(snapshot, transfer.source)
    private val positions = PixelTransferPositions(transfer)

    fun plan(): PixelTransferRasterizationResult {
        var count = 0
        visitChanges { _, _, _ -> count++ }
        if (count == 0) return PixelTransferRasterizationResult.NoChanges
        val packedPositions = IntArray(count)
        val before = ByteArray(count)
        val after = ByteArray(count)
        var cursor = 0
        visitChanges { pixel, previous, next ->
            packedPositions[cursor] = packPatchPosition(pixel, previous and COVERED_BIT != 0, next and COVERED_BIT != 0)
            before[cursor] = previous.toByte()
            after[cursor] = next.toByte()
            cursor++
        }
        return when (
            val result =
                PixelPatch.createFromValidatedPackedIndices(
                    transfer.canvas,
                    packedPositions,
                    before,
                    after,
                    false,
                )
        ) {
            is PixelPatchCreationResult.Created -> PixelTransferRasterizationResult.Rasterized(result.patch)
            is PixelPatchCreationResult.Rejected -> error("Admitted transfer patch rejected: ${result.rejection}")
        }
    }

    private fun visitChanges(consume: (Int, Int, Int) -> Unit) {
        positions.forEachIndex { pixel ->
            val position =
                PixelTransferCells.position(
                    pixel % transfer.canvas.width.value,
                    pixel / transfer.canvas.width.value,
                )
            val previous =
                when (val result = snapshot.cellAt(position)) {
                    is DomainValueResult.Created -> result.value.packed()
                    is DomainValueResult.Rejected -> error("Admitted transfer position rejected: ${result.rejection}")
                }
            val next = cells.afterAt(pixel, transfer)
            if (previous != next) consume(pixel, previous, next)
        }
    }

    private fun PixelCell.packed(): Int =
        when (this) {
            PixelCell.Empty -> 0
            is PixelCell.Covered -> COVERED_BIT or index.value
        }

    private companion object {
        const val COVERED_BIT: Int = PixelTransferCells.COVERED_BIT
    }
}
