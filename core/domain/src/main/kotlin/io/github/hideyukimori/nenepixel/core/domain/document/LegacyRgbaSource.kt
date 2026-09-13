package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class LegacyRgbaSource private constructor(
    public val id: DocumentId,
    public val revision: Revision,
    public val size: CanvasSize,
    private val packedRgba8888: IntArray,
) {
    public fun colorAt(position: PixelPosition): DomainValueResult<PixelColor> =
        if (size.contains(position)) {
            created(PixelColor.fromPackedRgba8888(packedRgba8888[position.rowMajorIndex(size)]))
        } else {
            rejected(DomainValueRejection.PixelPositionOutsideCanvas(size, position))
        }

    public fun copyPackedRgba8888(): IntArray = packedRgba8888.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is LegacyRgbaSource &&
                    id == other.id &&
                    revision == other.revision &&
                    size == other.size &&
                    packedRgba8888.contentEquals(other.packedRgba8888)
            )

    override fun hashCode(): Int =
        31 * (31 * (31 * id.hashCode() + revision.hashCode()) + size.hashCode()) +
            packedRgba8888.contentHashCode()

    override fun toString(): String = "LegacyRgbaSource(id=$id, revision=$revision, size=$size)"

    public companion object {
        public fun createPackedRgba8888(
            id: DocumentId,
            revision: Revision,
            size: CanvasSize,
            packedRgba8888: IntArray,
        ): DomainValueResult<LegacyRgbaSource> =
            if (size.pixelCount == packedRgba8888.size.toLong()) {
                created(LegacyRgbaSource(id, revision, size, packedRgba8888.copyOf()))
            } else {
                rejected(DomainValueRejection.LegacyRgbaSourceSizeMismatch(size.pixelCount, packedRgba8888.size))
            }
    }
}

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value
