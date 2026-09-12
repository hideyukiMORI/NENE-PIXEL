package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class PaletteRemap private constructor(
    public val source: PaletteDefinition,
    public val target: PaletteDefinition,
    private val destinations: List<PaletteIndex>,
) {
    public fun destinationAt(sourceIndex: PaletteIndex): DomainValueResult<PaletteIndex> =
        if (sourceIndex.value >= destinations.size) {
            rejected(DomainValueRejection.PaletteIndexOutsidePalette(sourceIndex, destinations.size))
        } else {
            created(destinations[sourceIndex.value])
        }

    public fun destinations(): List<PaletteIndex> = destinations.toList()

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PaletteRemap && source == other.source && target == other.target &&
                    destinations == other.destinations
            )

    override fun hashCode(): Int = 31 * (31 * source.hashCode() + target.hashCode()) + destinations.hashCode()

    override fun toString(): String =
        "PaletteRemap(sourceCount=${source.palette.entryCount}, targetCount=${target.palette.entryCount})"

    public companion object {
        public fun create(
            source: PaletteDefinition,
            target: PaletteDefinition,
            destinations: List<PaletteIndex>,
        ): DomainValueResult<PaletteRemap> {
            if (destinations.size != source.palette.entryCount) {
                return rejected(
                    DomainValueRejection.PaletteRemapSizeMismatch(source.palette.entryCount, destinations.size),
                )
            }
            val owned = destinations.toList()
            val outside = owned.indexOfFirst { it.value >= target.palette.entryCount }
            return if (outside >= 0) {
                rejected(
                    DomainValueRejection.PaletteRemapDestinationOutsidePalette(
                        PaletteIndex.createWithinPalette(outside),
                        owned[outside],
                        target.palette.entryCount,
                    ),
                )
            } else {
                created(PaletteRemap(source, target, owned))
            }
        }
    }
}
