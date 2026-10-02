package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

/**
 * An immutable raster read from an imported picture, before it becomes part of a document
 * (ADR 0033).
 *
 * The raster is straight sRGB RGBA8888 in row-major order; each value packs red, green, blue and
 * alpha from the high byte to the low byte, like `PixelColor.toPackedRgba8888`. The backing array
 * never leaves this class. Equality is identity: two rasters are the same only when they are the
 * same instance.
 */
public class ImportRaster private constructor(
    public val width: Int,
    public val height: Int,
    private val packedRgba8888: IntArray,
) {
    public fun copyPackedRgba8888(): IntArray = packedRgba8888.copyOf()

    override fun toString(): String = "ImportRaster(width=$width, height=$height)"

    public companion object {
        public const val MAX_SIDE: Int = 1024

        /** Copies [packedRgba8888] after the size checks; never throws (KOT-007, ARC-008). */
        public fun create(
            width: Int,
            height: Int,
            packedRgba8888: IntArray,
        ): DomainValueResult<ImportRaster> =
            when {
                !isValidSide(width) || !isValidSide(height) -> {
                    rejected(DomainValueRejection.ImportRasterSideOutOfRange(width, height))
                }

                packedRgba8888.size != width * height -> {
                    rejected(DomainValueRejection.ImportRasterSizeMismatch(width * height, packedRgba8888.size))
                }

                else -> {
                    created(ImportRaster(width, height, packedRgba8888.copyOf()))
                }
            }

        private fun isValidSide(side: Int): Boolean = side in MIN_SIDE..MAX_SIDE

        private const val MIN_SIDE: Int = 1
    }
}
