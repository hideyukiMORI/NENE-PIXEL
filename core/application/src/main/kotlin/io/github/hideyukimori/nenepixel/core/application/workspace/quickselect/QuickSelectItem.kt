package io.github.hideyukimori.nenepixel.core.application.workspace.quickselect

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/** One choice of the quick-select menu (ADR 0029); a later choice adds a case and its reduction. */
public sealed interface QuickSelectItem {
    public data class PaletteSlot(
        val index: PaletteIndex,
    ) : QuickSelectItem

    public data object Eyedropper : QuickSelectItem
}
