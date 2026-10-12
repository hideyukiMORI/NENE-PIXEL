package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object FrameTestValues {
    val white: PixelColor =
        ColorChannel.create(CHANNEL_MAX).frameValue().let { channel ->
            PixelColor.create(channel, channel, channel, channel)
        }

    fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).frameValue(), PixelY.create(y).frameValue())

    private const val CHANNEL_MAX = 255
}

internal fun <T> DomainValueResult<T>.frameValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid frame UI fixture: $rejection")
    }
