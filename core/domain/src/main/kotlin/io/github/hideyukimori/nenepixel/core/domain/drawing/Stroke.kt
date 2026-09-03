package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class Stroke private constructor(
    public val canvas: CanvasSize,
    private val path: List<PixelPosition>,
    public val color: PixelColor,
) {
    public val positionCount: Int
        get() = path.size

    public fun forEachPosition(action: (PixelPosition) -> Unit) {
        path.forEach(action)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Stroke && canvas == other.canvas && path == other.path && color == other.color)

    override fun hashCode(): Int =
        ((canvas.hashCode() * HASH_MULTIPLIER) + path.hashCode()) * HASH_MULTIPLIER + color.hashCode()

    override fun toString(): String = "Stroke(canvas=$canvas, positionCount=$positionCount, color=$color)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            canvas: CanvasSize,
            path: List<PixelPosition>,
            color: PixelColor,
        ): DomainValueResult<Stroke> =
            when {
                path.isEmpty() -> {
                    rejected(DomainValueRejection.EmptyStrokePath)
                }

                path.size > PixelLimits.MAX_RAW_STROKE_POSITIONS -> {
                    rejected(
                        DomainValueRejection.StrokePathAboveSupportedMaximum(
                            path.size,
                            PixelLimits.MAX_RAW_STROKE_POSITIONS,
                        ),
                    )
                }

                else -> {
                    createWithinVolume(canvas, path, color)
                }
            }

        private fun createWithinVolume(
            canvas: CanvasSize,
            path: List<PixelPosition>,
            color: PixelColor,
        ): DomainValueResult<Stroke> {
            val outside = path.firstOrNull { position -> !canvas.contains(position) }
            return if (outside == null) {
                created(Stroke(canvas, path.toList(), color))
            } else {
                rejected(DomainValueRejection.PixelPositionOutsideCanvas(canvas, outside))
            }
        }
    }
}
