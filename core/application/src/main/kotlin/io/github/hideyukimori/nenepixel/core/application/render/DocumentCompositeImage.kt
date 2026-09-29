package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeRaster
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PackedRgbaTransform

/**
 * The document's Composite (ADR 0030) in row-major order. Each value uses the channel order of
 * `PixelColor.toPackedRgba8888`. The backing array never leaves the pixel engine.
 */
public class DocumentCompositeImage internal constructor(
    private val raster: CompositeRaster,
) {
    public val size: CanvasSize
        get() = raster.size

    public fun copyPackedRgba8888(): IntArray = raster.copyPackedRgba8888()

    /** A new array holding [transform] of every pixel in row-major order; the backing array stays private. */
    public fun mapPackedRgba8888(transform: CompositePixelTransform): IntArray =
        raster.mapPackedRgba8888(PackedRgbaTransform { packedRgba8888 -> transform.map(packedRgba8888) })
}
