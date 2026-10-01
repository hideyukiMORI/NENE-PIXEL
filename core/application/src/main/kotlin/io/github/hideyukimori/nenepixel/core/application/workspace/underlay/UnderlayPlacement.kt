package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import kotlin.math.max
import kotlin.math.min

/**
 * Where the reference underlay sits over the document (ADR 0032).
 *
 * [left] and [top] are the document-pixel coordinates of the image's top-left corner, and [scale]
 * is how many document pixels one image pixel covers. Every value is finite and [scale] is positive.
 * A negative zero offset is stored as positive zero, so equal placements share one hash code.
 */
public class UnderlayPlacement private constructor(
    left: Double,
    top: Double,
    public val scale: Double,
) {
    // Adding positive zero turns -0.0 into 0.0 and leaves every other value unchanged.
    public val left: Double = left + 0.0
    public val top: Double = top + 0.0

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is UnderlayPlacement &&
                    left == other.left &&
                    top == other.top &&
                    scale == other.scale
            )

    override fun hashCode(): Int =
        (left.hashCode() * HASH_MULTIPLIER + top.hashCode()) * HASH_MULTIPLIER + scale.hashCode()

    override fun toString(): String = "UnderlayPlacement(left=$left, top=$top, scale=$scale)"

    public companion object {
        /** The largest scale that shows the whole image inside [canvas], centred. */
        public fun fitted(
            image: ReferenceImage,
            canvas: CanvasSize,
        ): UnderlayPlacement {
            val canvasWidth = canvas.width.value.toDouble()
            val canvasHeight = canvas.height.value.toDouble()
            val scale = min(canvasWidth / image.width, canvasHeight / image.height)
            return UnderlayPlacement(
                left = (canvasWidth - image.width * scale) / 2.0,
                top = (canvasHeight - image.height * scale) / 2.0,
                scale = scale,
            )
        }

        /**
         * Clamps the requested placement instead of rejecting it (ADR 0032). Non-finite values and a
         * non-positive scale fall back to [fitted]; the shown image keeps at least a one-pixel
         * overlap with the document, or its whole extent when it is thinner than one pixel.
         */
        internal fun create(
            left: Double,
            top: Double,
            scale: Double,
            image: ReferenceImage,
            canvas: CanvasSize,
        ): UnderlayPlacement {
            val fitted = fitted(image, canvas)
            val clampedScale = clampedScale(scale, fitted.scale, image, canvas)
            return UnderlayPlacement(
                left = clampedOffset(left, fitted.left, image.width * clampedScale, canvas.width.value),
                top = clampedOffset(top, fitted.top, image.height * clampedScale, canvas.height.value),
                scale = clampedScale,
            )
        }

        private fun clampedScale(
            requested: Double,
            fittedScale: Double,
            image: ReferenceImage,
            canvas: CanvasSize,
        ): Double {
            val scale = if (requested.isFinite() && requested > 0.0) requested else fittedScale
            val imageLongSide = max(image.width, image.height).toDouble()
            val canvasLongSide = max(canvas.width.value, canvas.height.value).toDouble()
            val lower = min(imageLongSide * fittedScale, canvasLongSide / MIN_SHOWN_DIVISOR)
            val upper = canvasLongSide * MAX_SHOWN_MULTIPLIER
            val shownLongSide = imageLongSide * scale
            return when {
                shownLongSide < lower -> lower / imageLongSide
                shownLongSide > upper -> upper / imageLongSide
                else -> scale
            }
        }

        private fun clampedOffset(
            requested: Double,
            fittedOffset: Double,
            shownExtent: Double,
            canvasExtent: Int,
        ): Double {
            val offset = if (requested.isFinite()) requested else fittedOffset
            val overlap = min(MIN_OVERLAP, shownExtent)
            return offset.coerceIn(overlap - shownExtent, canvasExtent - overlap)
        }

        private const val MIN_SHOWN_DIVISOR: Double = 8.0
        private const val MAX_SHOWN_MULTIPLIER: Double = 16.0
        private const val MIN_OVERLAP: Double = 1.0
        private const val HASH_MULTIPLIER: Int = 31
    }
}
