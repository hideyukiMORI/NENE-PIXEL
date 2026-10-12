package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface

/** Copies visible sources once for one disposable, single-consumer palette preview workspace. */
public fun preparePaletteComposite(
    size: CanvasSize,
    layers: List<Layer>,
    definition: PaletteDefinition,
): PaletteCompositeResult =
    when (val rejection = compositeRejection(size, layers, definition.palette.entryCount)) {
        null -> {
            val surfaces =
                layers
                    .filter { it.visibility == LayerVisibility.Visible }
                    .map { PixelSurface.from(it.snapshot) }
                    .toTypedArray()
            PaletteCompositeResult.Prepared(PaletteComposite(size, paletteRgba(definition), surfaces))
        }

        else -> {
            PaletteCompositeResult.Rejected(rejection)
        }
    }
