package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class PaletteDefinition private constructor(
    public val palette: Palette,
    public val defaultIndex: PaletteIndex,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is PaletteDefinition && palette == other.palette && defaultIndex == other.defaultIndex)

    override fun hashCode(): Int = 31 * palette.hashCode() + defaultIndex.hashCode()

    override fun toString(): String = "PaletteDefinition(entryCount=${palette.entryCount}, defaultIndex=$defaultIndex)"

    public companion object {
        public fun create(
            palette: Palette,
            defaultIndex: PaletteIndex,
        ): DomainValueResult<PaletteDefinition> =
            when {
                palette.entryCount < PaletteLimits.MIN_DEFINITION_ENTRY_COUNT -> {
                    rejected(
                        DomainValueRejection.PaletteBelowDefinitionMinimum(
                            palette.entryCount,
                            PaletteLimits.MIN_DEFINITION_ENTRY_COUNT,
                        ),
                    )
                }

                defaultIndex.value >= palette.entryCount -> {
                    rejected(DomainValueRejection.PaletteIndexOutsidePalette(defaultIndex, palette.entryCount))
                }

                else -> {
                    created(PaletteDefinition(palette, defaultIndex))
                }
            }
    }
}
