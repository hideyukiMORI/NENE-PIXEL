package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

private const val U8_MASK: Int = 0xff
private const val COVERAGE_BYTE_SHIFT: Int = 3
private const val COVERAGE_BIT_MASK: Int = 7

/**
 * The single composite of ADR 0030. [layers] are ordered bottom to top. Hidden layers do not
 * contribute but are still validated for size and palette range.
 */
public fun compositeLayers(
    size: CanvasSize,
    layers: List<Layer>,
    definition: PaletteDefinition,
): CompositeResult =
    when (val rejection = compositeRejection(size, layers, definition.palette.entryCount)) {
        null -> CompositeResult.Composited(CompositeRaster(size, composite(size, layers, definition)))
        else -> CompositeResult.Rejected(rejection)
    }

private fun compositeRejection(
    size: CanvasSize,
    layers: List<Layer>,
    entryCount: Int,
): CompositeRejection? =
    if (layers.isEmpty()) {
        CompositeRejection.NoLayers
    } else {
        layers.firstNotNullOfOrNull { layer -> layerRejection(size, layer, entryCount) }
    }

private fun layerRejection(
    size: CanvasSize,
    layer: Layer,
    entryCount: Int,
): CompositeRejection? =
    when {
        layer.snapshot.size != size -> {
            CompositeRejection.LayerSizeMismatch(layer.id, size, layer.snapshot.size)
        }

        layer.snapshot.maximumIndex.value >= entryCount -> {
            CompositeRejection.IndexOutsidePalette(layer.id, layer.snapshot.maximumIndex)
        }

        else -> {
            null
        }
    }

private fun composite(
    size: CanvasSize,
    layers: List<Layer>,
    definition: PaletteDefinition,
): IntArray {
    val accumulator = CompositeAccumulator(size.pixelCount.toInt(), paletteRgba(definition))
    layers
        .filter { layer -> layer.visibility == LayerVisibility.Visible }
        .forEach { layer -> accumulator.add(layer.snapshot) }
    return accumulator.packedRgba
}

private fun paletteRgba(definition: PaletteDefinition): IntArray {
    val entries = definition.palette.entries()
    return IntArray(entries.size) { entries[it].color.toPackedRgba8888() }
}

/** Mutable per-pixel work state. It stays inside this file and is handed off only as the raster. */
private class CompositeAccumulator(
    pixelCount: Int,
    private val palette: IntArray,
) {
    val packedRgba: IntArray = IntArray(pixelCount)
    private val contributed: BooleanArray = BooleanArray(pixelCount)

    fun add(snapshot: PixelSnapshot) {
        val indices = snapshot.copyPackedIndices()
        val coverage = snapshot.copyCoverage()
        for (pixel in indices.indices) {
            if (coverage.isCoveredAt(pixel)) {
                addColor(pixel, palette[indices[pixel].toInt() and U8_MASK])
            }
        }
    }

    private fun addColor(
        pixel: Int,
        sourceRgba: Int,
    ) {
        if (contributed[pixel]) {
            packedRgba[pixel] = blendOver(packedRgba[pixel], sourceRgba)
        } else {
            packedRgba[pixel] = sourceRgba
            contributed[pixel] = true
        }
    }

    private fun ByteArray.isCoveredAt(pixel: Int): Boolean =
        (this[pixel ushr COVERAGE_BYTE_SHIFT].toInt() ushr (pixel and COVERAGE_BIT_MASK)) and 1 == 1
}
