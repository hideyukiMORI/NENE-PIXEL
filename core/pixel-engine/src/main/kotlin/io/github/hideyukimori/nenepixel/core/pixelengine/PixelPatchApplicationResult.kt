package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public sealed interface PixelPatchApplicationResult {
    public data class Applied internal constructor(
        public val snapshot: PixelSnapshot,
    ) : PixelPatchApplicationResult

    public data class Rejected internal constructor(
        public val rejection: PixelPatchApplicationRejection,
    ) : PixelPatchApplicationResult
}
