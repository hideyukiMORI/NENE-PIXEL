package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

/** Application-owned reason a palette draft operation was rejected. */
public sealed interface PaletteDraftRejection {
    public data class InvalidDefinition internal constructor(
        public val rejection: DomainValueRejection,
    ) : PaletteDraftRejection

    public data class OrderSizeMismatch internal constructor(
        public val expectedCount: Int,
        public val attemptedCount: Int,
    ) : PaletteDraftRejection

    public data class OrderIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteDraftRejection

    public data class RepeatedOrderIndex internal constructor(
        public val index: PaletteIndex,
    ) : PaletteDraftRejection

    public data class RemovedIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteDraftRejection

    public data class ReplacementIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteDraftRejection

    public data object ReplacementIsRemovedIndex : PaletteDraftRejection

    public data object BelowDefinitionMinimum : PaletteDraftRejection

    public data object NoUndoAvailable : PaletteDraftRejection

    public data object NoRedoAvailable : PaletteDraftRejection
}
