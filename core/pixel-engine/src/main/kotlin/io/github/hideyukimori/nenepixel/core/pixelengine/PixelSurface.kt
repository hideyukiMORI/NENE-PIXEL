package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class PixelSurface private constructor(
    private val size: CanvasSize,
    private val pixels: MutableList<PixelColor>,
) {
    fun colorAt(position: PixelPosition): PixelColor = pixels[position.rowMajorIndex(size)]

    fun write(change: PixelChange) {
        pixels[change.position.rowMajorIndex(size)] = change.after
    }

    fun snapshot(revision: Revision): PixelSnapshot = PixelSnapshot.create(size, revision, pixels).requiredValue()

    companion object {
        fun from(snapshot: PixelSnapshot): PixelSurface {
            val pixelCount = snapshot.size.pixelCount.toInt()
            val ownedPixels =
                MutableList(pixelCount) { index ->
                    val position = snapshot.size.positionAt(index)
                    snapshot.colorAt(position).requiredValue()
                }
            return PixelSurface(snapshot.size, ownedPixels)
        }

        private fun CanvasSize.positionAt(index: Int): PixelPosition =
            PixelPosition.create(
                x = PixelX.create(index % width.value).requiredValue(),
                y = PixelY.create(index / width.value).requiredValue(),
            )
    }
}

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int =
    (y.value.toLong() * size.width.value.toLong() + x.value.toLong()).toInt()

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated pixel-engine invariant was rejected: $rejection")
    }
