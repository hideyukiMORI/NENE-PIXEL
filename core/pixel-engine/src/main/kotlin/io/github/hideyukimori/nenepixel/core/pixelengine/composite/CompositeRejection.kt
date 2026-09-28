package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface CompositeRejection {
    public data object NoLayers : CompositeRejection

    public data class LayerSizeMismatch internal constructor(
        public val layerId: LayerId,
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : CompositeRejection

    public data class IndexOutsidePalette internal constructor(
        public val layerId: LayerId,
        public val index: PaletteIndex,
    ) : CompositeRejection
}
