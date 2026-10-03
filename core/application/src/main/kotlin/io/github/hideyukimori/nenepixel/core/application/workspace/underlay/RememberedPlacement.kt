package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

/**
 * The remembered placement of a work's reference underlay (ADR 0034): [left] and [top] are the
 * document-pixel coordinates of the image's top-left corner, and [scale] is how many document pixels
 * one image pixel covers.
 *
 * Unlike [UnderlayPlacement], it is not clamped against a canvas or an image; the clamp happens when
 * the underlay is restored. [create] is the only validator of the numbers. A negative zero offset is
 * stored as positive zero, so equal placements share one hash code.
 */
public class RememberedPlacement private constructor(
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
                other is RememberedPlacement &&
                    left == other.left &&
                    top == other.top &&
                    scale == other.scale
            )

    override fun hashCode(): Int =
        (left.hashCode() * HASH_MULTIPLIER + top.hashCode()) * HASH_MULTIPLIER + scale.hashCode()

    override fun toString(): String = "RememberedPlacement(left=$left, top=$top, scale=$scale)"

    public companion object {
        /**
         * Accepts finite [left] and [top] and a finite [scale] above zero; never throws
         * (KOT-007, ADR 0034).
         */
        public fun create(
            left: Double,
            top: Double,
            scale: Double,
        ): RememberedPlacementResult =
            when {
                !left.isFinite() || !top.isFinite() -> rejected(RememberedPlacementRejection.NonFiniteOffset)
                !scale.isFinite() || scale <= 0.0 -> rejected(RememberedPlacementRejection.InvalidScale)
                else -> RememberedPlacementResult.Created(RememberedPlacement(left, top, scale))
            }

        private fun rejected(reason: RememberedPlacementRejection): RememberedPlacementResult =
            RememberedPlacementResult.Rejected(reason)

        private const val HASH_MULTIPLIER: Int = 31
    }
}
