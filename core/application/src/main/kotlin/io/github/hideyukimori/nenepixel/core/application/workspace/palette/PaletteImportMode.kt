package io.github.hideyukimori.nenepixel.core.application.workspace.palette

/** How an imported palette maps the current draft slots onto its slots (ADR 0022 Replace). */
public sealed interface PaletteImportMode {
    /** Slot `i` keeps number `i` while the import has it; every source outside the import needs an assignment. */
    public data object ByNumber : PaletteImportMode

    /** Every source slot moves to its deterministic nearest import color; assignments are ignored. */
    public data object Nearest : PaletteImportMode

    /** Every source slot needs an explicit assignment; many-to-one is allowed. */
    public data object Explicit : PaletteImportMode
}
