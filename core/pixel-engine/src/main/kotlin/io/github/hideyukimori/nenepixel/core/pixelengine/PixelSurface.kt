package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class PixelSurface private constructor(
    private val size: CanvasSize,
    private val packedIndices: ByteArray,
) {
    fun indexAt(position: PixelPosition): PaletteIndex =
        PaletteIndex.create(packedIndices[position.rowMajorIndex(size)].toInt() and U8_MASK).requiredValue()

    fun packedIndexAt(rowMajorIndex: Int): Byte = packedIndices[rowMajorIndex]

    fun write(change: PixelChange) {
        packedIndices[change.position.rowMajorIndex(size)] = change.after.value.toByte()
    }

    fun writePackedIndex(
        rowMajorIndex: Int,
        value: Byte,
    ) {
        packedIndices[rowMajorIndex] = value
    }

    fun snapshot(revision: Revision): PixelSnapshot =
        PixelSnapshot.createPackedIndices(size, revision, packedIndices).requiredValue()

    companion object {
        private const val U8_MASK: Int = 0xff

        fun from(snapshot: PixelSnapshot): PixelSurface = PixelSurface(snapshot.size, snapshot.copyPackedIndices())
    }
}

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated pixel-engine invariant was rejected: $rejection")
    }
