package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

public sealed interface StrokeEffect {
    public data class Paint(
        public val color: PixelColor,
    ) : StrokeEffect

    public data object Erase : StrokeEffect
}
