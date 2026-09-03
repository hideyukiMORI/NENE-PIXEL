package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

public class PaletteEntry internal constructor(
    public val index: PaletteIndex,
    public val color: PixelColor,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PaletteEntry && index == other.index && color == other.color)

    override fun hashCode(): Int = index.hashCode() * HASH_MULTIPLIER + color.hashCode()

    override fun toString(): String = "PaletteEntry(index=$index, color=$color)"

    private companion object {
        const val HASH_MULTIPLIER: Int = 31
    }
}
