package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface

/** Unique covered positions per origin slot, including transparent and occluded contributions. */
internal class PaletteCompositeDependencies(
    private val surfaces: Array<PixelSurface>,
    private val pixelCount: Int,
    entryCount: Int,
) {
    private val seen = IntArray(entryCount) { -1 }
    private val counts = IntArray(entryCount)
    private val positions: Array<IntArray>

    init {
        visit { slot, _ -> counts[slot]++ }
        positions = Array(entryCount) { IntArray(counts[it]) }
        counts.fill(0)
        seen.fill(-1)
        visit { slot, pixel -> positions[slot][counts[slot]++] = pixel }
    }

    val positionCount: Int = counts.sum()

    fun at(slot: Int): IntArray = positions[slot]

    private inline fun visit(consume: (Int, Int) -> Unit) {
        for (pixel in 0 until pixelCount) {
            for (surface in surfaces) {
                if (!surface.isCoveredAt(pixel)) continue
                val slot = surface.packedIndexAt(pixel).toInt() and U8_MASK
                if (seen[slot] != pixel) {
                    seen[slot] = pixel
                    consume(slot, pixel)
                }
            }
        }
    }

    private companion object {
        const val U8_MASK: Int = 0xff
    }
}
