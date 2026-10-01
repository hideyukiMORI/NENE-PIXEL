package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/**
 * The palette a layer import writes into and the slot of each region colour, in the order of
 * `RasterImportRegion.colors`.
 */
internal class RasterColorMapping(
    val target: PaletteDefinition,
    val slots: IntArray,
    val nearestColorCount: Int,
)
