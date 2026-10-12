package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferCells

/** Validates once, captures the selected cells and copies only the other visible surfaces once. */
public fun preparePixelTransferComposite(
    size: CanvasSize,
    layers: List<Layer>,
    definition: PaletteDefinition,
    target: PixelTransferCompositeTarget,
): PixelTransferCompositeResult {
    val rejection = compositeRejection(size, layers, definition.palette.entryCount)
    if (rejection != null) return PixelTransferCompositeResult.Rejected(rejection)
    val layer =
        layers.firstOrNull { it.id == target.layerId && it.visibility == LayerVisibility.Visible }
    return if (layer == null) {
        PixelTransferCompositeResult.TargetLayerNotVisible(target.layerId)
    } else {
        when (val region = PixelRegion.create(size, target.source.origin, target.source.size)) {
            is DomainValueResult.Rejected -> {
                PixelTransferCompositeResult.SourceRegionRejected(region.rejection)
            }

            is DomainValueResult.Created -> {
                val surfaces = visibleSurfaces(layers, target.layerId)
                PixelTransferCompositeResult.Prepared(
                    PixelTransferComposite(
                        size,
                        paletteRgba(definition),
                        surfaces,
                        PixelTransferCells(layer.snapshot, target.source),
                    ),
                )
            }
        }
    }
}
