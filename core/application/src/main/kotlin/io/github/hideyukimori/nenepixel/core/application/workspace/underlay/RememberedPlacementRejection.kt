package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

/** Why [RememberedPlacement.create] refused its numbers (ADR 0034). */
public sealed interface RememberedPlacementRejection {
    /** The left or the top is NaN or infinite. */
    public data object NonFiniteOffset : RememberedPlacementRejection

    /** The scale is NaN, infinite, zero or negative. */
    public data object InvalidScale : RememberedPlacementRejection
}
