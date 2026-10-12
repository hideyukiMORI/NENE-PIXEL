package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface

/**
 * Disposable, non-thread-safe palette workspace for unchanged layer snapshots. Mutable buffers
 * remain private; every published raster is immutable. Only affected positions are recomposited.
 */
public class PaletteComposite internal constructor(
    private val size: CanvasSize,
    private var palette: IntArray,
    private val surfaces: Array<PixelSurface>,
) {
    private val pixelCount = size.pixelCount.toInt()
    private val dependencies = PaletteCompositeDependencies(surfaces, pixelCount, palette.size)
    private val rgba = IntArray(pixelCount) { compositeAt(it) }
    private val affected = BooleanArray(pixelCount)
    private val affectedPositions = IntArray(pixelCount)
    private var raster = CompositeRaster(size, rgba.copyOf())

    internal val dependencyPositionCount: Int
        get() = dependencies.positionCount

    internal var lastEvaluatedPixelCount: Int = 0
        private set

    /**
     * [definition] must have the prepared origin's entry count. Unchanged effective colours (or
     * changed unused slots) reuse the raster; changed output publishes one canvas-sized copy.
     */
    public fun render(definition: PaletteDefinition): CompositeRaster {
        require(definition.palette.entryCount == palette.size) { "Palette table must retain the origin entry count" }
        val next = paletteRgba(definition)
        val count = collectAffected(next)
        palette = next
        lastEvaluatedPixelCount = count
        if (recompose(count)) raster = CompositeRaster(size, rgba.copyOf())
        return raster
    }

    private fun collectAffected(next: IntArray): Int {
        var count = 0
        for (slot in next.indices) {
            if (next[slot] == palette[slot]) continue
            for (pixel in dependencies.at(slot)) {
                if (!affected[pixel]) {
                    affected[pixel] = true
                    affectedPositions[count++] = pixel
                }
            }
        }
        return count
    }

    private fun recompose(count: Int): Boolean {
        var changed = false
        for (position in 0 until count) {
            val pixel = affectedPositions[position]
            val next = compositeAt(pixel)
            if (next != rgba[pixel]) {
                rgba[pixel] = next
                changed = true
            }
            affected[pixel] = false
        }
        return changed
    }

    private fun compositeAt(pixel: Int): Int {
        var result = 0
        var contributed = false
        for (surface in surfaces) {
            if (surface.isCoveredAt(pixel)) {
                val source = palette[surface.packedIndexAt(pixel).toInt() and U8_MASK]
                result = contributeOver(contributed, result, source)
                contributed = true
            }
        }
        return result
    }

    private companion object {
        const val U8_MASK: Int = 0xff
    }
}
