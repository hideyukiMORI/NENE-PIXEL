package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

internal data class LayerPanelRowsModel(
    val rows: List<LayerRowModel>,
    val active: LayerId,
    val totalLayerCount: Int,
)
