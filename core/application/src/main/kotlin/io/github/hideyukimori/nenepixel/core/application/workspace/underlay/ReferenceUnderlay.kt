package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * The session-only reference picture under the drawing: the image, the document size it was placed
 * against, where it sits, how strongly it shows, and whether it is shown and being adjusted
 * (ADR 0032). Equality compares the values; the image compares by identity.
 */
public class ReferenceUnderlay private constructor(
    private val source: UnderlaySource,
    private val presentation: UnderlayPresentation,
) {
    public val image: ReferenceImage get() = source.image
    public val canvas: CanvasSize get() = source.canvas
    public val placement: UnderlayPlacement get() = presentation.placement
    public val opacity: UnderlayOpacity get() = presentation.opacity
    public val visibility: UnderlayVisibility get() = presentation.visibility
    public val interaction: UnderlayInteraction get() = presentation.interaction

    public fun withOpacity(opacity: UnderlayOpacity): ReferenceUnderlay =
        ReferenceUnderlay(source, presentation.copy(opacity = opacity))

    /** Clamps the requested placement against this image and canvas. */
    public fun withPlacement(
        left: Double,
        top: Double,
        scale: Double,
    ): ReferenceUnderlay =
        ReferenceUnderlay(
            source,
            presentation.copy(
                placement = UnderlayPlacement.create(RequestedUnderlayPlacement(left, top, scale), source),
            ),
        )

    public fun fitted(): ReferenceUnderlay =
        ReferenceUnderlay(source, presentation.copy(placement = UnderlayPlacement.fitted(image, canvas)))

    /** Hiding also ends any adjustment. */
    public fun toggledVisibility(): ReferenceUnderlay =
        when (visibility) {
            UnderlayVisibility.Shown -> withVisibility(UnderlayVisibility.Hidden).rested()
            UnderlayVisibility.Hidden -> withVisibility(UnderlayVisibility.Shown)
        }

    /** Only a shown underlay can be adjusted; a hidden one is returned unchanged. */
    public fun adjusting(): ReferenceUnderlay =
        when (visibility) {
            UnderlayVisibility.Shown -> withInteraction(UnderlayInteraction.Adjusting)
            UnderlayVisibility.Hidden -> this
        }

    public fun rested(): ReferenceUnderlay = withInteraction(UnderlayInteraction.Resting)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ReferenceUnderlay &&
                    image === other.image &&
                    canvas == other.canvas &&
                    placement == other.placement &&
                    opacity == other.opacity &&
                    visibility == other.visibility &&
                    interaction == other.interaction
            )

    override fun hashCode(): Int =
        listOf(image, canvas, placement, opacity, visibility, interaction)
            .fold(0) { hash, value -> hash * HASH_MULTIPLIER + value.hashCode() }

    override fun toString(): String =
        "ReferenceUnderlay(image=$image, canvas=$canvas, placement=$placement, opacity=$opacity, " +
            "visibility=$visibility, interaction=$interaction)"

    private fun withVisibility(visibility: UnderlayVisibility): ReferenceUnderlay =
        ReferenceUnderlay(source, presentation.copy(visibility = visibility))

    private fun withInteraction(interaction: UnderlayInteraction): ReferenceUnderlay =
        ReferenceUnderlay(source, presentation.copy(interaction = interaction))

    public companion object {
        /** Fitted to [canvas], at the default opacity, shown and resting. */
        public fun placed(
            image: ReferenceImage,
            canvas: CanvasSize,
        ): ReferenceUnderlay =
            ReferenceUnderlay(
                UnderlaySource(image, canvas),
                UnderlayPresentation(
                    UnderlayPlacement.fitted(image, canvas),
                    UnderlayOpacity.DEFAULT,
                    UnderlayVisibility.Shown,
                    UnderlayInteraction.Resting,
                ),
            )

        private const val HASH_MULTIPLIER: Int = 31
    }
}

private data class UnderlayPresentation(
    val placement: UnderlayPlacement,
    val opacity: UnderlayOpacity,
    val visibility: UnderlayVisibility,
    val interaction: UnderlayInteraction,
)
