package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * What the device remembers of a work's reference underlay (ADR 0034): the exact image, the
 * placement, the opacity and the visibility. The interaction and the canvas size are not remembered;
 * [toUnderlay] restores a resting underlay clamped against the given canvas through the existing
 * [ReferenceUnderlay] derivations. Equality compares the values; the image compares by identity, as
 * in [ReferenceUnderlay].
 */
public class RememberedUnderlay private constructor(
    public val image: ReferenceImage,
    public val placement: RememberedPlacement,
    public val opacity: UnderlayOpacity,
    public val visibility: UnderlayVisibility,
) {
    /**
     * Places [image] on [canvas], then applies the placement (clamped there), the opacity and, for a
     * hidden one, the visibility. The result is always resting.
     */
    public fun toUnderlay(canvas: CanvasSize): ReferenceUnderlay {
        val shown =
            ReferenceUnderlay
                .placed(image, canvas)
                .withPlacement(placement.left, placement.top, placement.scale)
                .withOpacity(opacity)
        return when (visibility) {
            UnderlayVisibility.Shown -> shown
            UnderlayVisibility.Hidden -> shown.toggledVisibility()
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is RememberedUnderlay &&
                    image === other.image &&
                    placement == other.placement &&
                    opacity == other.opacity &&
                    visibility == other.visibility
            )

    override fun hashCode(): Int =
        listOf(image, placement, opacity, visibility)
            .fold(0) { hash, value -> hash * HASH_MULTIPLIER + value.hashCode() }

    override fun toString(): String =
        "RememberedUnderlay(image=$image, placement=$placement, opacity=$opacity, visibility=$visibility)"

    public companion object {
        /** Every argument is already validated, so this never fails. */
        public fun create(
            image: ReferenceImage,
            placement: RememberedPlacement,
            opacity: UnderlayOpacity,
            visibility: UnderlayVisibility,
        ): RememberedUnderlay = RememberedUnderlay(image, placement, opacity, visibility)

        /** Keeps the same image instance and the current values of [underlay]; drops its interaction. */
        public fun of(underlay: ReferenceUnderlay): RememberedUnderlay =
            RememberedUnderlay(
                underlay.image,
                RememberedPlacement
                    .create(underlay.placement.left, underlay.placement.top, underlay.placement.scale)
                    .requiredPlacement(),
                underlay.opacity,
                underlay.visibility,
            )

        private const val HASH_MULTIPLIER: Int = 31
    }
}

private fun RememberedPlacementResult.requiredPlacement(): RememberedPlacement =
    when (this) {
        is RememberedPlacementResult.Created -> placement
        is RememberedPlacementResult.Rejected -> error("Underlay placement invariant was rejected: $reason")
    }
