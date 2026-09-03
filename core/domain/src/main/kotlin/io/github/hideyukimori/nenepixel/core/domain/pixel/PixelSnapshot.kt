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
    private val packedPixels: IntArray,
) {
    public fun colorAt(position: PixelPosition): DomainValueResult<PixelColor> =
        if (size.contains(position)) {
            created(PixelColor.fromPackedRgba8888(packedPixels[position.rowMajorIndex(size)]))
        } else {
            rejected(DomainValueRejection.PixelPositionOutsideCanvas(size, position))
        }

    public fun packedRgba8888At(position: PixelPosition): DomainValueResult<Int> =
        if (size.contains(position)) {
            created(packedPixels[position.rowMajorIndex(size)])
        } else {
            rejected(DomainValueRejection.PixelPositionOutsideCanvas(size, position))
        }

    public fun copyPackedRgba8888(): IntArray = packedPixels.copyOf()

    public fun mapPackedRgba8888(
        revision: Revision,
        transform: (rowMajorIndex: Int, packedRgba8888: Int) -> Int,
    ): PixelSnapshot =
        PixelSnapshot(
            size,
            revision,
            IntArray(packedPixels.size) { index -> transform(index, packedPixels[index]) },
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PixelSnapshot &&
                    size == other.size &&
                    revision == other.revision &&
                    packedPixels.contentEquals(other.packedPixels)
            )

    override fun hashCode(): Int =
        ((size.hashCode() * HASH_MULTIPLIER) + revision.hashCode()) * HASH_MULTIPLIER +
            packedPixels.contentHashCode()

    override fun toString(): String = "PixelSnapshot(size=$size, revision=$revision)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            size: CanvasSize,
            revision: Revision,
            pixels: List<PixelColor>,
        ): DomainValueResult<PixelSnapshot> =
            if (size.pixelCount == pixels.size.toLong()) {
                created(
                    PixelSnapshot(
                        size,
                        revision,
                        IntArray(pixels.size) { index -> pixels[index].toPackedRgba8888() },
                    ),
                )
            } else {
                rejected(DomainValueRejection.PixelSnapshotSizeMismatch(size.pixelCount, pixels.size))
            }

        public fun createPackedRgba8888(
            size: CanvasSize,
            revision: Revision,
            packedPixels: IntArray,
        ): DomainValueResult<PixelSnapshot> =
            if (size.pixelCount == packedPixels.size.toLong()) {
                created(PixelSnapshot(size, revision, packedPixels.copyOf()))
            } else {
                rejected(DomainValueRejection.PixelSnapshotSizeMismatch(size.pixelCount, packedPixels.size))
            }

        private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value
    }
}
