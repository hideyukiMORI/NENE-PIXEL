package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public data class PixelChange private constructor(
    public val position: PixelPosition,
    public val before: PixelColor,
    public val after: PixelColor,
) {
    internal fun inverse(): PixelChange = PixelChange(position, after, before)

    public companion object {
        public fun create(
            position: PixelPosition,
            before: PixelColor,
            after: PixelColor,
        ): PixelChange = PixelChange(position, before, after)
    }
}
