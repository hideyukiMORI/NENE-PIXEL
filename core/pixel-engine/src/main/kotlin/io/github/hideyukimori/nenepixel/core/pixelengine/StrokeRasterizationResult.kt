package io.github.hideyukimori.nenepixel.core.pixelengine

public sealed interface StrokeRasterizationResult {
    public data class Rasterized internal constructor(
        public val patch: PixelPatch,
    ) : StrokeRasterizationResult

    public data object NoChanges : StrokeRasterizationResult

    public data class Rejected internal constructor(
        public val rejection: StrokeRasterizationRejection,
    ) : StrokeRasterizationResult
}
