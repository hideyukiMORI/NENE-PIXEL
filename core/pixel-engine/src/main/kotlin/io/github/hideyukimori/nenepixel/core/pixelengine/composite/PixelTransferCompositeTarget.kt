package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Captured visible layer and selected source geometry; it owns no mutable document cells. */
public class PixelTransferCompositeTarget(
    public val layerId: LayerId,
    public val source: PixelRegion,
)
