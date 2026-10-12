package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits

public data class CanvasSize private constructor(
    public val width: CanvasWidth,
    public val height: CanvasHeight,
) {
    public val pixelCount: Long
        get() = width.value.toLong() * height.value.toLong()

    init {
        check(pixelCount <= PixelLimits.MAX_CANVAS_PIXELS) {
            "Validated canvas axes exceeded the supported pixel count."
        }
    }

    public fun contains(position: PixelPosition): Boolean =
        position.x.value < width.value && position.y.value < height.value

    /** True when the signed point lies in the half-open canvas rectangle [0, width) x [0, height). */
    public fun contains(point: DocumentPoint): Boolean =
        point.x >= 0 && point.y >= 0 && point.x < width.value && point.y < height.value

    public companion object {
        public fun create(
            width: CanvasWidth,
            height: CanvasHeight,
        ): CanvasSize = CanvasSize(width, height)
    }
}
