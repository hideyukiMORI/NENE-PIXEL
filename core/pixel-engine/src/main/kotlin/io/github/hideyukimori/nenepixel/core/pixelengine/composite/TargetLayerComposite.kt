package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface

/** Shared per-position replacement kernel; the null visible surface is the substituted target layer. */
internal class TargetLayerComposite(
    private val palette: IntArray,
    private val surfaces: Array<PixelSurface?>,
) {
    fun at(
        pixel: Int,
        targetCovered: Boolean,
        targetRgba: Int,
    ): Int {
        var rgba = 0
        var contributed = false
        for (surface in surfaces) {
            val covered = if (surface == null) targetCovered else surface.isCoveredAt(pixel)
            if (covered) {
                val source =
                    if (surface ==
                        null
                    ) {
                        targetRgba
                    } else {
                        palette[surface.packedIndexAt(pixel).toInt() and INDEX_MASK]
                    }
                rgba = contributeOver(contributed, rgba, source)
                contributed = true
            }
        }
        return rgba
    }

    private companion object {
        const val INDEX_MASK: Int = 0xff
    }
}
