package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface PixelCell {
    public data object Empty : PixelCell

    public data class Covered(
        public val index: PaletteIndex,
    ) : PixelCell
}
