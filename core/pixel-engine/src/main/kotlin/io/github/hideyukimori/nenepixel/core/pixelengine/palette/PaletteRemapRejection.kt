package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

public sealed interface PaletteRemapRejection {
    public data class InvalidMapping internal constructor(
        public val rejection: DomainValueRejection,
    ) : PaletteRemapRejection

    public data class OrderSizeMismatch internal constructor(
        public val expectedCount: Int,
        public val attemptedCount: Int,
    ) : PaletteRemapRejection

    public data class OrderIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteRemapRejection

    public data class RepeatedOrderIndex internal constructor(
        public val index: PaletteIndex,
    ) : PaletteRemapRejection

    public data class RemovedIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteRemapRejection

    public data class ReplacementIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteRemapRejection

    public data object ReplacementIsRemovedIndex : PaletteRemapRejection

    public data object BelowDefinitionMinimum : PaletteRemapRejection
}
