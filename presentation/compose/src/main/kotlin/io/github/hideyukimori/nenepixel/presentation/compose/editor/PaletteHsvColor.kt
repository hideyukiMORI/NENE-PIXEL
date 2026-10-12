package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** Derived picker coordinates only: grey retains hue and black retains saturation (ADR 0022, #168). */
internal data class PaletteHsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColor(alpha: Int): PixelColor {
        val chroma = value * saturation
        val sector = hue / HUE_SECTOR
        val second = chroma * (1f - abs(sector % 2f - 1f))
        val rgb = sectorComponents(sector, chroma, second)
        val minimum = value - chroma
        val channels = rgb.map { ((it + minimum) * CHANNEL_MAX).roundToInt().coerceIn(0, CHANNEL_MAX) }
        return PixelColor.fromPackedRgba8888(
            (channels[0] shl RED_SHIFT) or (channels[1] shl GREEN_SHIFT) or
                (channels[2] shl BLUE_SHIFT) or alpha.coerceIn(0, CHANNEL_MAX),
        )
    }

    companion object {
        const val FULL_HUE: Float = 360f
        const val CHANNEL_MAX: Int = 255
        private const val HUE_SECTOR: Float = 60f
        private const val RED_SHIFT: Int = 24
        private const val GREEN_SHIFT: Int = 16
        private const val BLUE_SHIFT: Int = 8
        private const val RGB_MASK: Int = -256
        private const val HUE_SECTOR_COUNT: Int = 6
        private const val GREEN_SECTOR: Int = 2
        private const val CYAN_SECTOR: Int = 3
        private const val BLUE_SECTOR: Int = 4

        fun fromColor(
            color: PixelColor,
            previous: PaletteHsvColor = PaletteHsvColor(0f, 0f, 0f),
        ): PaletteHsvColor {
            val red =
                color.red.value
                    .toInt()
                    .toFloat() / CHANNEL_MAX
            val green =
                color.green.value
                    .toInt()
                    .toFloat() / CHANNEL_MAX
            val blue =
                color.blue.value
                    .toInt()
                    .toFloat() / CHANNEL_MAX
            val maximum = maxOf(red, green, blue)
            val chroma = maximum - minOf(red, green, blue)
            val hue = if (chroma == 0f) previous.hue else hueOf(red, green, blue, chroma)
            val saturation = if (maximum == 0f) previous.saturation else chroma / maximum
            return PaletteHsvColor(hue, saturation, maximum)
        }

        /** Alpha updates use exact existing RGB, including hidden RGB, without HSV quantization. */
        fun withAlpha(
            color: PixelColor,
            fraction: Float,
        ): PixelColor {
            val alpha = (fraction.coerceIn(0f, 1f) * CHANNEL_MAX).roundToInt()
            return PixelColor.fromPackedRgba8888((color.toPackedRgba8888() and RGB_MASK) or alpha)
        }

        private fun hueOf(
            red: Float,
            green: Float,
            blue: Float,
            chroma: Float,
        ): Float {
            val sector =
                when (maxOf(red, green, blue)) {
                    red -> (green - blue) / chroma
                    green -> (blue - red) / chroma + GREEN_SECTOR
                    else -> (red - green) / chroma + BLUE_SECTOR
                }
            return ((sector * HUE_SECTOR) + FULL_HUE) % FULL_HUE
        }

        private fun sectorComponents(
            sector: Float,
            chroma: Float,
            second: Float,
        ): List<Float> =
            when (sector.toInt() % HUE_SECTOR_COUNT) {
                0 -> listOf(chroma, second, 0f)
                1 -> listOf(second, chroma, 0f)
                GREEN_SECTOR -> listOf(0f, chroma, second)
                CYAN_SECTOR -> listOf(0f, second, chroma)
                BLUE_SECTOR -> listOf(second, 0f, chroma)
                else -> listOf(chroma, 0f, second)
            }
    }
}
