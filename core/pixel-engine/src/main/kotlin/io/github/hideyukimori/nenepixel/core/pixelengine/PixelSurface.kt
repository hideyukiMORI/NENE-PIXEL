package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class PixelSurface private constructor(
    private val size: CanvasSize,
    private val packedPixels: IntArray,
) {
    fun colorAt(position: PixelPosition): PixelColor =
        PixelColor.fromPackedRgba8888(packedPixels[position.rowMajorIndex(size)])

    fun packedRgba8888At(rowMajorIndex: Int): Int = packedPixels[rowMajorIndex]

    fun write(change: PixelChange) {
        packedPixels[change.position.rowMajorIndex(size)] = change.after.toPackedRgba8888()
    }

    fun writePackedRgba8888(
        rowMajorIndex: Int,
        value: Int,
    ) {
        packedPixels[rowMajorIndex] = value
    }

    fun snapshot(revision: Revision): PixelSnapshot =
        PixelSnapshot.createPackedRgba8888(size, revision, packedPixels).requiredValue()

    companion object {
        fun from(snapshot: PixelSnapshot): PixelSurface = PixelSurface(snapshot.size, snapshot.copyPackedRgba8888())
    }
}

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated pixel-engine invariant was rejected: $rejection")
    }
