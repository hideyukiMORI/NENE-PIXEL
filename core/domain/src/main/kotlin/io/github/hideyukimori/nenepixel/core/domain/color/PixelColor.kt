package io.github.hideyukimori.nenepixel.core.domain.color

public data class PixelColor private constructor(
    public val red: ColorChannel,
    public val green: ColorChannel,
    public val blue: ColorChannel,
    public val alpha: ColorChannel,
) {
    public companion object {
        public fun create(
            red: ColorChannel,
            green: ColorChannel,
            blue: ColorChannel,
            alpha: ColorChannel,
        ): PixelColor = PixelColor(red, green, blue, alpha)
    }
}
