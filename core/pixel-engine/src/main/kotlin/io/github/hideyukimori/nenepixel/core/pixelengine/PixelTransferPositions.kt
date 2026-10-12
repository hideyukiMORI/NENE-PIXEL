package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode

/** Ordered union of two rectangles; it never visits the empty gap of their bounding box. */
internal class PixelTransferPositions(
    private val transfer: PixelTransfer,
) {
    fun forEachIndex(consume: (Int) -> Unit) {
        val source = RectangleCursor(transfer.canvas.width.value, transfer.source)
        val destination = RectangleCursor(transfer.canvas.width.value, transfer.destinationRegion)
        var left = if (transfer.mode == PixelTransferMode.Move) source.next() else END
        var right = destination.next()
        while (left != END || right != END) {
            val position = minOf(left, right)
            consume(position)
            if (left == position) left = source.next()
            if (right == position) right = destination.next()
        }
    }

    private class RectangleCursor(
        private val stride: Int,
        private val region: PixelRegion,
    ) {
        private var cursor = 0

        fun next(): Int {
            if (cursor == region.size.pixelCount.toInt()) return END
            val index = cursor++
            val x = region.origin.x.value + index % region.size.width.value
            val y = region.origin.y.value + index / region.size.width.value
            return y * stride + x
        }
    }

    private companion object {
        const val END: Int = Int.MAX_VALUE
    }
}
