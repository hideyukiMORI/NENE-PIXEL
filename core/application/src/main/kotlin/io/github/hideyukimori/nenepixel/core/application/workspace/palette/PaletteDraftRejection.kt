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

    /** The draft was opened against a runtime source that is no longer current. */
    public data object StaleBase : PaletteDraftRejection

    /** An import transition was requested while no import is pending. */
    public data object NoPendingImport : PaletteDraftRejection

    /** Draft edits and draft history wait until the pending import is confirmed or cancelled. */
    public data object ImportPending : PaletteDraftRejection

    /** The pending import cannot be confirmed until each of these draft slots has a destination. */
    public data class UnresolvedImportSources internal constructor(
        public val sources: List<PaletteIndex>,
    ) : PaletteDraftRejection

    public data class ImportSourceOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteDraftRejection

    public data class ImportDestinationOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : PaletteDraftRejection
}
