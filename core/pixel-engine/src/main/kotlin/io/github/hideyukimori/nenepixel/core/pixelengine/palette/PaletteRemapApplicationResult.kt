package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch

public sealed interface PaletteRemapApplicationResult {
    public data class Changed internal constructor(
        public val patch: PixelPatch,
    ) : PaletteRemapApplicationResult

    public data object NoIndexChanges : PaletteRemapApplicationResult

    public data class Rejected internal constructor(
        public val rejection: PaletteRemapApplicationRejection,
    ) : PaletteRemapApplicationResult
}
