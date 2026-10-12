package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Frozen selected cells. Private primitive arrays are captured once, then never mutated. */
internal class PixelTransferCells(
    snapshot: PixelSnapshot,
    private val source: PixelRegion,
) {
    private val indices = ByteArray(source.size.pixelCount.toInt())
    private val coverage = ByteArray((indices.size + COVERAGE_MASK) / BITS_PER_BYTE)

    init {
        for (pixel in indices.indices) {
            val x = source.origin.x.value + pixel % source.size.width.value
            val y = source.origin.y.value + pixel / source.size.width.value
            val cell = snapshot.cellAt(position(x, y)).required()
            if (cell is PixelCell.Covered) {
                indices[pixel] = cell.index.value.toByte()
                val byte = pixel / BITS_PER_BYTE
                coverage[byte] = (coverage[byte].toInt() or (1 shl (pixel and COVERAGE_MASK))).toByte()
            }
        }
    }

    /** Shared final-cell rule. Called only for the transfer's affected union; destination always wins. */
    fun afterAt(
        pixel: Int,
        transfer: PixelTransfer,
    ): Int {
        val x = pixel % transfer.canvas.width.value
        val y = pixel / transfer.canvas.width.value
        val target = transfer.destinationRegion
        val localX = x - target.origin.x.value
        val localY = y - target.origin.y.value
        return if (localX in 0 until source.size.width.value && localY in 0 until source.size.height.value) {
            val local = localY * source.size.width.value + localX
            val covered = (coverage[local / BITS_PER_BYTE].toInt() ushr (local and COVERAGE_MASK)) and 1 == 1
            if (covered) COVERED_BIT or (indices[local].toInt() and INDEX_MASK) else 0
        } else {
            // The union's other positions are Move's source-only cells.
            0
        }
    }

    fun requireSource(transfer: PixelTransfer) {
        require(transfer.source == source) { "Transfer source changed after preparation" }
    }

    companion object {
        const val COVERED_BIT: Int = 1 shl 8
        const val INDEX_MASK: Int = 0xff
        private const val COVERAGE_MASK: Int = 7
        private const val BITS_PER_BYTE: Int = 8

        fun position(
            x: Int,
            y: Int,
        ): PixelPosition = PixelPosition.create(PixelX.create(x).required(), PixelY.create(y).required())

        private fun <T> DomainValueResult<T>.required(): T =
            when (this) {
                is DomainValueResult.Created -> value
                is DomainValueResult.Rejected -> error("Admitted transfer cell rejected: $rejection")
            }
    }
}
