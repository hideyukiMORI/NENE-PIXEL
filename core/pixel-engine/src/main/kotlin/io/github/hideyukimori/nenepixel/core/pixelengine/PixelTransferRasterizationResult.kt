package io.github.hideyukimori.nenepixel.core.pixelengine

/** Exact transfer planning publishes one ordinary patch, or leaves its immutable source unchanged. */
public sealed interface PixelTransferRasterizationResult {
    public class Rasterized internal constructor(
        public val patch: PixelPatch,
    ) : PixelTransferRasterizationResult

    public data object NoChanges : PixelTransferRasterizationResult

    public class Rejected internal constructor(
        public val rejection: PixelTransferRasterizationRejection,
    ) : PixelTransferRasterizationResult
}
