package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/** One draft-only palette edit (ADR 0022 operation table); the session plans it into a draft-history entry. */
public sealed interface PaletteDraftOperation {
    public data class SetSlotColor(
        public val index: PaletteIndex,
        public val color: PixelColor,
    ) : PaletteDraftOperation

    public data class SetDefault(
        public val index: PaletteIndex,
    ) : PaletteDraftOperation

    public data class AppendSlot(
        public val color: PixelColor,
    ) : PaletteDraftOperation

    public data class RemoveSlot(
        public val removed: PaletteIndex,
        public val replacement: PaletteIndex? = null,
    ) : PaletteDraftOperation

    public data class Reorder(
        public val newOrder: List<PaletteIndex>,
    ) : PaletteDraftOperation
}
