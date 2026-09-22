package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/** Immutable conversion choices supplied by the app composition; never the live palette owner. */
public class LegacyPalettePresets(
    public val dusk: PaletteDefinition,
    public val grayscale: PaletteDefinition,
    public val swatches: PaletteDefinition,
)
