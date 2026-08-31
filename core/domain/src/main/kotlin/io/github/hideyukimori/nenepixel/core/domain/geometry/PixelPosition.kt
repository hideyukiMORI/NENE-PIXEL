package io.github.hideyukimori.nenepixel.core.domain.geometry

public data class PixelPosition private constructor(
    public val x: PixelX,
    public val y: PixelY,
) {
    public companion object {
        public fun create(
            x: PixelX,
            y: PixelY,
        ): PixelPosition = PixelPosition(x, y)
    }
}
