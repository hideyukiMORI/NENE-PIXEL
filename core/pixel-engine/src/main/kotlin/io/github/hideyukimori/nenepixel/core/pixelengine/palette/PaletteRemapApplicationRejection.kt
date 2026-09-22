package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface PaletteRemapApplicationRejection {
    public data class SourceIndexOutsidePalette internal constructor(
        public val position: PixelPosition,
        public val index: PaletteIndex,
        public val entryCount: Int,
    ) : PaletteRemapApplicationRejection

    public data object RevisionOverflow : PaletteRemapApplicationRejection
}
