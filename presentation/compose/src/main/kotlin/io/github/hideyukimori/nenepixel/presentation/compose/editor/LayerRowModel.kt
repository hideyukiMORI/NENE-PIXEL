package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/**
 * One layer-panel row (#144): only what a row shows, so committing a stroke (which replaces a layer's snapshot)
 * leaves the row list structurally equal. An empty [name] stays empty; the fallback label belongs to the row.
 */
internal data class LayerRowModel(
    val id: LayerId,
    val name: LayerName,
    val visibility: LayerVisibility,
)

/** The rows of [document] with the front-most layer first; linear in the layer count, never reads a snapshot. */
internal fun layerRowsOf(document: DocumentState): List<LayerRowModel> =
    document.layers.asReversed().map { layer -> LayerRowModel(layer.id, layer.name, layer.visibility) }
