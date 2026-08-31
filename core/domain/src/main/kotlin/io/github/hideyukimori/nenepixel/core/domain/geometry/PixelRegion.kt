package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class PixelRegion private constructor(
    public val origin: PixelPosition,
    public val size: CanvasSize,
) {
    public fun contains(position: PixelPosition): Boolean =
        position.x.value >= origin.x.value &&
            position.y.value >= origin.y.value &&
            position.x.value.toLong() < origin.x.value.toLong() + size.width.value.toLong() &&
            position.y.value.toLong() < origin.y.value.toLong() + size.height.value.toLong()

    override fun equals(other: Any?): Boolean =
        this === other || (other is PixelRegion && origin == other.origin && size == other.size)

    override fun hashCode(): Int = origin.hashCode() * HASH_MULTIPLIER + size.hashCode()

    override fun toString(): String = "PixelRegion(origin=$origin, size=$size)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            canvas: CanvasSize,
            origin: PixelPosition,
            size: CanvasSize,
        ): DomainValueResult<PixelRegion> =
            if (canvas.containsRegion(origin, size)) {
                created(PixelRegion(origin, size))
            } else {
                rejected(DomainValueRejection.PixelRegionOutsideCanvas(canvas, origin, size))
            }

        private fun CanvasSize.containsRegion(
            origin: PixelPosition,
            regionSize: CanvasSize,
        ): Boolean =
            origin.x.value.toLong() + regionSize.width.value.toLong() <= width.value.toLong() &&
                origin.y.value.toLong() + regionSize.height.value.toLong() <= height.value.toLong()
    }
}
