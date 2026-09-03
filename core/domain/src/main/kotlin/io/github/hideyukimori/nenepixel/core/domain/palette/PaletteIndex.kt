package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class PaletteIndex private constructor(
    public val value: Int,
) {
    public companion object {
        public val first: PaletteIndex = PaletteIndex(0)

        public fun create(value: Int): DomainValueResult<PaletteIndex> =
            if (value < 0) {
                rejected(DomainValueRejection.NegativePaletteIndex(value))
            } else {
                created(PaletteIndex(value))
            }

        internal fun createWithinPalette(value: Int): PaletteIndex = PaletteIndex(value)
    }
}
