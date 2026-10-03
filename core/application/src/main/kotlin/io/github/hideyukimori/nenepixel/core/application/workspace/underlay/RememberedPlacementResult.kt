package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

/** The closed outcome of [RememberedPlacement.create] (ADR 0034). */
public sealed interface RememberedPlacementResult {
    public data class Created internal constructor(
        public val placement: RememberedPlacement,
    ) : RememberedPlacementResult

    public data class Rejected internal constructor(
        public val reason: RememberedPlacementRejection,
    ) : RememberedPlacementResult
}
