package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

/**
 * An immutable reference picture shown under the drawing (ADR 0032).
 *
 * The raster is straight sRGB RGBA8888 in row-major order; each value packs red, green, blue and
 * alpha from the high byte to the low byte, like `PixelColor.toPackedRgba8888` and
 * `CompositeRaster.copyPackedRgba8888`. The backing array never leaves this class. Equality is
 * identity: two images are the same only when they are the same instance.
 */
public class ReferenceImage private constructor(
    public val width: Int,
    public val height: Int,
    private val packedRgba: IntArray,
) {
    public fun copyPackedRgba8888(): IntArray = packedRgba.copyOf()

    override fun toString(): String = "ReferenceImage(width=$width, height=$height)"

    public companion object {
        public const val MAX_SIDE: Int = 1024

        /** Copies [packedRgba8888] after the size checks; never throws (KOT-007, ARC-008). */
        public fun create(
            width: Int,
            height: Int,
            packedRgba8888: IntArray,
        ): ReferenceImageResult =
            when {
                !isValidSide(width) || !isValidSide(height) -> rejected(ReferenceImageRejection.InvalidSize)
                packedRgba8888.size != width * height -> rejected(ReferenceImageRejection.PixelCountMismatch)
                else -> ReferenceImageResult.Created(ReferenceImage(width, height, packedRgba8888.copyOf()))
            }

        private fun rejected(reason: ReferenceImageRejection): ReferenceImageResult =
            ReferenceImageResult.Rejected(reason)

        private fun isValidSide(side: Int): Boolean = side in MIN_SIDE..MAX_SIDE

        private const val MIN_SIDE: Int = 1
    }
}
