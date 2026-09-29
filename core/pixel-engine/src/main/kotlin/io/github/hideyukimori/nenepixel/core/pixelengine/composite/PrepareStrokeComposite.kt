package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeTarget

/**
 * Prepares [StrokeComposite] for [target]. Checks run in order: the same validation as
 * [compositeLayers], then the target layer must be present and visible, then a Paint index must be
 * inside the palette.
 */
public fun prepareStrokeComposite(
    size: CanvasSize,
    layers: List<Layer>,
    definition: PaletteDefinition,
    target: StrokeCompositeTarget,
): StrokeCompositeResult {
    val entryCount = definition.palette.entryCount
    val effect = target.effect
    val rejection = compositeRejection(size, layers, entryCount)
    return when {
        rejection != null -> {
            StrokeCompositeResult.Rejected(rejection)
        }

        layers.none { layer -> layer.id == target.layerId && layer.visibility == LayerVisibility.Visible } -> {
            StrokeCompositeResult.TargetLayerNotVisible(target.layerId)
        }

        effect is StrokeEffect.Paint && effect.targetIndex.value >= entryCount -> {
            StrokeCompositeResult.TargetIndexOutsidePalette(effect.targetIndex)
        }

        else -> {
            StrokeCompositeResult.Prepared(
                StrokeComposite(
                    size,
                    paletteRgba(definition),
                    visibleSurfaces(layers, target.layerId),
                    StrokeTarget.of(effect),
                ),
            )
        }
    }
}

/** Visible layers bottom to top; the target slot is `null` so its cells are never copied. */
private fun visibleSurfaces(
    layers: List<Layer>,
    targetLayerId: LayerId,
): Array<PixelSurface?> =
    layers
        .filter { layer -> layer.visibility == LayerVisibility.Visible }
        .map { layer -> if (layer.id == targetLayerId) null else PixelSurface.from(layer.snapshot) }
        .toTypedArray()
