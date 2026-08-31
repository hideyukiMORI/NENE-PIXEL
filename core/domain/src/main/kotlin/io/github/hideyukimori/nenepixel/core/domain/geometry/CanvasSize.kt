package io.github.hideyukimori.nenepixel.core.domain.geometry

public data class CanvasSize private constructor(
    public val width: CanvasWidth,
    public val height: CanvasHeight,
) {
    public val pixelCount: Long
        get() = width.value.toLong() * height.value.toLong()

    public fun contains(position: PixelPosition): Boolean =
        position.x.value < width.value && position.y.value < height.value

    public companion object {
        public fun create(
            width: CanvasWidth,
            height: CanvasHeight,
        ): CanvasSize = CanvasSize(width, height)
    }
}
