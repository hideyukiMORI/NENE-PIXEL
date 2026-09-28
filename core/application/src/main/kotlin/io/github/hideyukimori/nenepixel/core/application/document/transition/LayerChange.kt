package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

internal data class LayerChange(
    val layerId: LayerId,
    val changes: LayerIndexChanges,
) {
    fun inverse(): LayerChange = LayerChange(layerId, changes.inverse())
}
