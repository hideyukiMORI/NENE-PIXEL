package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

public data class PaletteEntry private constructor(
    public val index: PaletteIndex,
    public val color: PixelColor,
) {
    public companion object {
        public fun create(
            index: PaletteIndex,
            color: PixelColor,
        ): PaletteEntry = PaletteEntry(index, color)
    }
}
