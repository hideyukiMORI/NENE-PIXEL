package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class PixelSnapshot private constructor(
    public val size: CanvasSize,
    public val revision: Revision,
    private val pixels: List<PixelColor>,
) {
    public fun colorAt(position: PixelPosition): DomainValueResult<PixelColor> =
        if (size.contains(position)) {
            created(pixels[position.rowMajorIndex(size)])
        } else {
            rejected(DomainValueRejection.PixelPositionOutsideCanvas(size, position))
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is PixelSnapshot && size == other.size && revision == other.revision && pixels == other.pixels)

    override fun hashCode(): Int =
        ((size.hashCode() * HASH_MULTIPLIER) + revision.hashCode()) * HASH_MULTIPLIER + pixels.hashCode()

    override fun toString(): String = "PixelSnapshot(size=$size, revision=$revision)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            size: CanvasSize,
            revision: Revision,
            pixels: List<PixelColor>,
        ): DomainValueResult<PixelSnapshot> =
            if (size.pixelCount == pixels.size.toLong()) {
                created(PixelSnapshot(size, revision, pixels.toList()))
            } else {
                rejected(DomainValueRejection.PixelSnapshotSizeMismatch(size.pixelCount, pixels.size))
            }

        private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int =
            (y.value.toLong() * size.width.value.toLong() + x.value.toLong()).toInt()
    }
}
