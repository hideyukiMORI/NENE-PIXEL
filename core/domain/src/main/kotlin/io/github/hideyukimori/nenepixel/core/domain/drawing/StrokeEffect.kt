package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface StrokeEffect {
    public val targetIndex: PaletteIndex

    public data class Paint(
        override val targetIndex: PaletteIndex,
    ) : StrokeEffect

    public data class Erase(
        override val targetIndex: PaletteIndex,
    ) : StrokeEffect
}
