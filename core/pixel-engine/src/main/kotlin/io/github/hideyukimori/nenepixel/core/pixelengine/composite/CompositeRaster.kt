package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * The composited visible image in row-major order. Each value uses the channel order of
 * `PixelColor.toPackedRgba8888`. The backing array never leaves this class.
 */
public class CompositeRaster internal constructor(
    public val size: CanvasSize,
    private val packedRgba: IntArray,
) {
    public fun copyPackedRgba8888(): IntArray = packedRgba.copyOf()

    /** A new array holding [transform] of every pixel in row-major order; the backing array stays private. */
    public fun mapPackedRgba8888(transform: PackedRgbaTransform): IntArray =
        IntArray(packedRgba.size) { pixel -> transform.map(packedRgba[pixel]) }
}
