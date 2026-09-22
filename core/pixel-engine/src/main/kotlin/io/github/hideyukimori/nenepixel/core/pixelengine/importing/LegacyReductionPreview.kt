package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public class LegacyReductionPreview internal constructor(
    public val definition: PaletteDefinition,
    public val snapshot: PixelSnapshot,
)
