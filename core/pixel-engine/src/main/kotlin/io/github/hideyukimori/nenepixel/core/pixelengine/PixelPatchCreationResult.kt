package io.github.hideyukimori.nenepixel.core.pixelengine

public sealed interface PixelPatchCreationResult {
    public data class Created internal constructor(
        public val patch: PixelPatch,
    ) : PixelPatchCreationResult

    public data class Rejected internal constructor(
        public val rejection: PixelPatchCreationRejection,
    ) : PixelPatchCreationResult
}
