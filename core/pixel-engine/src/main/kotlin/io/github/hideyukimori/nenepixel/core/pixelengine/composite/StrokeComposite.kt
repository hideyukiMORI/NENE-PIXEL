package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeTarget

private const val U8_MASK: Int = 0xff

/**
 * The Composite of ADR 0030 with every cell of one visible layer replaced by the cell a stroke
 * writes, evaluated one pixel at a time.
 *
 * [surfaces] holds the visible layers bottom to top; the `null` slot is the stroke target layer,
 * whose own cells are never read.
 */
public class StrokeComposite internal constructor(
    public val size: CanvasSize,
    palette: IntArray,
    surfaces: Array<PixelSurface?>,
    target: StrokeTarget,
) {
    private val pixelCount: Int = size.pixelCount.toInt()
    private val targetCovered: Boolean = target.covered
    private val targetRgba: Int = palette[target.packedIndex.toInt() and U8_MASK]
    private val composite = TargetLayerComposite(palette, surfaces)

    /** Packed RGBA8888 at the row-major [pixel]; `(0, 0, 0, 0)` when nothing contributes. */
    public fun packedRgba8888At(pixel: Int): Int {
        require(pixel in 0 until pixelCount) { "Pixel $pixel is outside 0 until $pixelCount" }
        return composite.at(pixel, targetCovered, targetRgba)
    }
}
