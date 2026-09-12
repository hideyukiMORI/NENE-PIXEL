package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

internal object NearestPaletteEntry {
    fun find(
        color: PixelColor,
        targets: List<PaletteEntry>,
    ): PaletteIndex = targets.firstOrNull { it.color == color }?.index ?: closest(color, targets)

    private fun closest(
        color: PixelColor,
        targets: List<PaletteEntry>,
    ): PaletteIndex {
        var chosen = PaletteIndex.first
        var minimum = Long.MAX_VALUE
        targets.forEach { target ->
            val distance = distance(color, target.color)
            if (distance < minimum) {
                minimum = distance
                chosen = target.index
            }
        }
        return chosen
    }

    private fun distance(
        left: PixelColor,
        right: PixelColor,
    ): Long {
        val leftAlpha = left.alpha.value.toLong()
        val rightAlpha = right.alpha.value.toLong()
        val red = left.red.value.toLong() * leftAlpha - right.red.value.toLong() * rightAlpha
        val green = left.green.value.toLong() * leftAlpha - right.green.value.toLong() * rightAlpha
        val blue = left.blue.value.toLong() * leftAlpha - right.blue.value.toLong() * rightAlpha
        val alpha = CHANNEL_MAXIMUM * (leftAlpha - rightAlpha)
        return red * red + green * green + blue * blue + ALPHA_WEIGHT * alpha * alpha
    }

    private const val CHANNEL_MAXIMUM: Long = 255L
    private const val ALPHA_WEIGHT: Long = 3L
}
