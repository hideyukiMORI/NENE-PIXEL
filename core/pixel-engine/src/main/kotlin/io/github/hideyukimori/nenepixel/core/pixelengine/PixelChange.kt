package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell

public data class PixelChange private constructor(
    public val position: PixelPosition,
    public val before: PixelCell,
    public val after: PixelCell,
) {
    internal fun inverse(): PixelChange = PixelChange(position, after, before)

    public companion object {
        public fun create(
            position: PixelPosition,
            before: PixelCell,
            after: PixelCell,
        ): PixelChange = PixelChange(position, before, after)
    }
}
