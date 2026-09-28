package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * The document's Composite (ADR 0030) in row-major order. Each value uses the channel order of
 * `PixelColor.toPackedRgba8888`. The backing array never leaves this class.
 */
public class DocumentCompositeImage internal constructor(
    public val size: CanvasSize,
    private val packedRgba: IntArray,
) {
    public fun copyPackedRgba8888(): IntArray = packedRgba.copyOf()
}
