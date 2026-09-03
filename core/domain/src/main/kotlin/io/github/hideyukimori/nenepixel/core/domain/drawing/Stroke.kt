package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class Stroke private constructor(
    public val canvas: CanvasSize,
    private val rowMajorPath: IntArray,
    public val effect: StrokeEffect,
) {
    public val positionCount: Int
        get() = rowMajorPath.size

    public fun forEachPosition(action: (PixelPosition) -> Unit) {
        rowMajorPath.forEach { index -> action(canvas.positionAt(index)) }
    }

    public fun rowMajorIndexAt(pathIndex: Int): Int = rowMajorPath[pathIndex]

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Stroke &&
                    canvas == other.canvas &&
                    rowMajorPath.contentEquals(other.rowMajorPath) &&
                    effect == other.effect
            )

    override fun hashCode(): Int =
        ((canvas.hashCode() * HASH_MULTIPLIER) + rowMajorPath.contentHashCode()) * HASH_MULTIPLIER + effect.hashCode()

    override fun toString(): String = "Stroke(canvas=$canvas, positionCount=$positionCount, effect=$effect)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            canvas: CanvasSize,
            path: List<PixelPosition>,
            effect: StrokeEffect,
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
                    createWithinVolume(canvas, path, effect)
                }
            }

        private fun createWithinVolume(
            canvas: CanvasSize,
            path: List<PixelPosition>,
            effect: StrokeEffect,
        ): DomainValueResult<Stroke> {
            val outside = path.firstOrNull { position -> !canvas.contains(position) }
            return if (outside == null) {
                created(
                    Stroke(
                        canvas,
                        IntArray(path.size) { index -> path[index].rowMajorIndex(canvas) },
                        effect,
                    ),
                )
            } else {
                rejected(DomainValueRejection.PixelPositionOutsideCanvas(canvas, outside))
            }
        }
    }
}

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun CanvasSize.positionAt(index: Int): PixelPosition =
    PixelPosition.create(
        PixelX.create(index % width.value).requiredValue(),
        PixelY.create(index / width.value).requiredValue(),
    )

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated stroke invariant was rejected: $rejection")
    }
