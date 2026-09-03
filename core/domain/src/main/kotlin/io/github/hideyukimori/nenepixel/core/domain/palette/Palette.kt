package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class Palette private constructor(
    colors: List<PixelColor>,
) {
    private val colors: List<PixelColor> = colors.toList()

    public val entryCount: Int
        get() = colors.size

    public fun entryAt(index: PaletteIndex): DomainValueResult<PaletteEntry> =
        if (index.value >= colors.size) {
            rejected(DomainValueRejection.PaletteIndexOutsidePalette(index, colors.size))
        } else {
            created(PaletteEntry(index, colors[index.value]))
        }

    private fun forEachEntry(action: (PaletteEntry) -> Unit) {
        colors.forEachIndexed { index, color ->
            action(PaletteEntry(PaletteIndex.createWithinPalette(index), color))
        }
    }

    public fun entries(): List<PaletteEntry> = buildList(entryCount) { forEachEntry(::add) }

    override fun equals(other: Any?): Boolean = this === other || (other is Palette && colors == other.colors)

    override fun hashCode(): Int = colors.hashCode()

    override fun toString(): String = "Palette(entryCount=$entryCount)"

    public companion object {
        public fun create(colors: List<PixelColor>): DomainValueResult<Palette> =
            when {
                colors.isEmpty() -> {
                    rejected(DomainValueRejection.EmptyPalette)
                }

                colors.size > PaletteLimits.MAX_ENTRY_COUNT -> {
                    rejected(
                        DomainValueRejection.PaletteAboveSupportedMaximum(
                            colors.size,
                            PaletteLimits.MAX_ENTRY_COUNT,
                        ),
                    )
                }

                else -> {
                    created(Palette(colors))
                }
            }
    }
}
