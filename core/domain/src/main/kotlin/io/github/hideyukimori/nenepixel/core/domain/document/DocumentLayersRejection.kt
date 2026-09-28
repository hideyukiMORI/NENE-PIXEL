package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal fun documentLayersRejection(
    definition: PaletteDefinition,
    layers: List<Layer>,
): DomainValueRejection? =
    layerCountRejection(layers.size)
        ?: duplicateLayerIdRejection(layers)
        ?: layerSizeRejection(layers)
        ?: layerPaletteRejection(definition, layers)

private fun layerCountRejection(count: Int): DomainValueRejection? =
    if (count in 1..LayerLimits.MAX_LAYERS) null else DomainValueRejection.DocumentLayerCountOutOfRange(count)

private fun duplicateLayerIdRejection(layers: List<Layer>): DomainValueRejection? {
    val seen = HashSet<LayerId>()
    return layers.firstOrNull { !seen.add(it.id) }?.let { DomainValueRejection.DuplicateLayerId(it.id.value) }
}

private fun layerSizeRejection(layers: List<Layer>): DomainValueRejection? {
    val expected = layers.first().snapshot.size
    return layers
        .firstOrNull { it.snapshot.size != expected }
        ?.let { DomainValueRejection.LayerSizeMismatch(it.id.value, expected, it.snapshot.size) }
}

private fun layerPaletteRejection(
    definition: PaletteDefinition,
    layers: List<Layer>,
): DomainValueRejection? =
    layers
        .asSequence()
        .map { definition.palette.entryAt(it.snapshot.maximumIndex) }
        .filterIsInstance<DomainValueResult.Rejected>()
        .firstOrNull()
        ?.rejection
