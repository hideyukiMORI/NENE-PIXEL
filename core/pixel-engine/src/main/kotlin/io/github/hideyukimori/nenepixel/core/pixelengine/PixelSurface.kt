package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class PixelSurface private constructor(
    private val size: CanvasSize,
    private val packedIndices: ByteArray,
    private val coverage: ByteArray,
) {
    fun cellAt(position: PixelPosition): PixelCell = cellAt(position.rowMajorIndex(size))

    fun cellAt(rowMajorIndex: Int): PixelCell =
        if (isCoveredAt(rowMajorIndex)) {
            PixelCell.Covered(
                PaletteIndex.create(packedIndices[rowMajorIndex].toInt() and U8_MASK).requiredValue(),
            )
        } else {
            PixelCell.Empty
        }

    fun isCoveredAt(rowMajorIndex: Int): Boolean =
        (coverage[rowMajorIndex ushr BYTE_SHIFT].toInt() ushr (rowMajorIndex and BIT_INDEX_MASK)) and 1 == 1

    fun packedIndexAt(rowMajorIndex: Int): Byte = packedIndices[rowMajorIndex]

    fun write(change: PixelChange) {
        writeCell(change.position.rowMajorIndex(size), change.after)
    }

    fun writeCell(
        rowMajorIndex: Int,
        cell: PixelCell,
    ) {
        when (cell) {
            PixelCell.Empty -> {
                writePackedCell(rowMajorIndex, covered = false, packedIndex = 0)
            }

            is PixelCell.Covered -> {
                writePackedCell(rowMajorIndex, covered = true, packedIndex = cell.index.value.toByte())
            }
        }
    }

    fun writePackedCell(
        rowMajorIndex: Int,
        covered: Boolean,
        packedIndex: Byte,
    ) {
        val byteIndex = rowMajorIndex ushr BYTE_SHIFT
        val bit = 1 shl (rowMajorIndex and BIT_INDEX_MASK)
        val current = coverage[byteIndex].toInt()
        if (covered) {
            coverage[byteIndex] = (current or bit).toByte()
            packedIndices[rowMajorIndex] = packedIndex
        } else {
            coverage[byteIndex] = (current and bit.inv()).toByte()
            packedIndices[rowMajorIndex] = 0
        }
    }

    fun snapshot(): PixelSnapshot = PixelSnapshot.createPackedCells(size, packedIndices, coverage).requiredValue()

    companion object {
        private const val U8_MASK: Int = 0xff
        private const val BIT_INDEX_MASK: Int = 7
        private const val BYTE_SHIFT: Int = 3

        fun from(snapshot: PixelSnapshot): PixelSurface =
            PixelSurface(snapshot.size, snapshot.copyPackedIndices(), snapshot.copyCoverage())
    }
}

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated pixel-engine invariant was rejected: $rejection")
    }
