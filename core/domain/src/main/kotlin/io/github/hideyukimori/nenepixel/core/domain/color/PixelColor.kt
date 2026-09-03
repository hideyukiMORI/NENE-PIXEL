package io.github.hideyukimori.nenepixel.core.domain.color

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public data class PixelColor private constructor(
    public val red: ColorChannel,
    public val green: ColorChannel,
    public val blue: ColorChannel,
    public val alpha: ColorChannel,
) {
    public fun toPackedRgba8888(): Int =
        (red.value.toInt() shl RED_SHIFT) or
            (green.value.toInt() shl GREEN_SHIFT) or
            (blue.value.toInt() shl BLUE_SHIFT) or
            alpha.value.toInt()

    public companion object {
        private const val CHANNEL_MASK: Int = 0xff
        private const val RED_SHIFT: Int = 24
        private const val GREEN_SHIFT: Int = 16
        private const val BLUE_SHIFT: Int = 8

        public val blank: PixelColor =
            PixelColor(
                channel(0),
                channel(0),
                channel(0),
                channel(0),
            )

        public fun create(
            red: ColorChannel,
            green: ColorChannel,
            blue: ColorChannel,
            alpha: ColorChannel,
        ): PixelColor = PixelColor(red, green, blue, alpha)

        public fun fromPackedRgba8888(value: Int): PixelColor =
            PixelColor(
                channel(value ushr RED_SHIFT),
                channel(value ushr GREEN_SHIFT),
                channel(value ushr BLUE_SHIFT),
                channel(value),
            )

        private fun channel(value: Int): ColorChannel =
            when (val result = ColorChannel.create(value and CHANNEL_MASK)) {
                is DomainValueResult.Created -> {
                    result.value
                }

                is DomainValueResult.Rejected -> {
                    error("Packed RGBA8 channel conversion was rejected: ${result.rejection}")
                }
            }
    }
}
