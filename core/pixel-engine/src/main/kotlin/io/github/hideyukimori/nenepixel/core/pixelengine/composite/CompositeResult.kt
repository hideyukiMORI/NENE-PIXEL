package io.github.hideyukimori.nenepixel.core.pixelengine.composite

public sealed interface CompositeResult {
    public data class Composited internal constructor(
        public val raster: CompositeRaster,
    ) : CompositeResult

    public data class Rejected internal constructor(
        public val rejection: CompositeRejection,
    ) : CompositeResult
}
