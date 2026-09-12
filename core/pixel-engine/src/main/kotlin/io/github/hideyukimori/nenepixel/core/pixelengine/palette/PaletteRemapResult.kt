package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public sealed interface PaletteRemapResult {
    public data class Planned internal constructor(
        public val remap: PaletteRemap,
    ) : PaletteRemapResult

    public data class Rejected internal constructor(
        public val rejection: PaletteRemapRejection,
    ) : PaletteRemapResult
}

internal fun planned(result: DomainValueResult<PaletteRemap>): PaletteRemapResult =
    when (result) {
        is DomainValueResult.Created -> PaletteRemapResult.Planned(result.value)
        is DomainValueResult.Rejected -> rejected(PaletteRemapRejection.InvalidMapping(result.rejection))
    }

internal fun rejected(rejection: PaletteRemapRejection): PaletteRemapResult = PaletteRemapResult.Rejected(rejection)
