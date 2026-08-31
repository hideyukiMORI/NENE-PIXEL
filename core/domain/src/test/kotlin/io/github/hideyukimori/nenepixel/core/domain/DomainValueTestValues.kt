package io.github.hideyukimori.nenepixel.core.domain

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY

internal object DomainValueTestValues {
    fun canvasSize(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(created(CanvasWidth.create(width)), created(CanvasHeight.create(height)))

    fun pixelPosition(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(created(PixelX.create(x)), created(PixelY.create(y)))

    fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            red = channel(red),
            green = channel(green),
            blue = channel(blue),
            alpha = channel(alpha),
        )

    fun channel(value: Int): ColorChannel = created(ColorChannel.create(value))
}
