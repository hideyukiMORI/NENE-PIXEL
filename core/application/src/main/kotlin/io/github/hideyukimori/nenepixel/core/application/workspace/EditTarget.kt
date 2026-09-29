package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/**
 * The workspace-owned active layer and active palette slot that drawing, erasing and the eyedropper target;
 * never saved (ADR 0030).
 */
public data class EditTarget private constructor(
    public val layerId: LayerId,
    public val paletteIndex: PaletteIndex,
) {
    public fun withLayer(layerId: LayerId): EditTarget = EditTarget(layerId, paletteIndex)

    public fun withPaletteIndex(paletteIndex: PaletteIndex): EditTarget = EditTarget(layerId, paletteIndex)

    public companion object {
        public fun create(
            layerId: LayerId,
            paletteIndex: PaletteIndex,
        ): EditTarget = EditTarget(layerId, paletteIndex)
    }
}
