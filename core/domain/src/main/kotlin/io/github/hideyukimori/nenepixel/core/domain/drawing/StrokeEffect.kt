package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface StrokeEffect {
    public data class Paint(
        val targetIndex: PaletteIndex,
    ) : StrokeEffect

    public data object Erase : StrokeEffect
}
