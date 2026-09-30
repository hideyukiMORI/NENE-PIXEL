package io.github.hideyukimori.nenepixel.core.application.workspace.quickselect

/** What a canvas pointer down means for the current workspace (ADR 0029, ADR 0032, CMD-010). */
public enum class CanvasPointerIntent {
    Draw,
    PickPaletteEntry,

    /** Pointer input moves and scales the shown reference underlay; no stroke starts and the viewport stays. */
    AdjustUnderlay,
}
