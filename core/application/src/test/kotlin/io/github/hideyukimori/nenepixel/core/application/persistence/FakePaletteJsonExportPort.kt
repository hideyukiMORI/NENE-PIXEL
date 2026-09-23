package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

internal class FakePaletteJsonExportPort : PaletteJsonExportPort {
    val definitions = mutableListOf<PaletteDefinition>()
    var handler: suspend (PaletteDefinition) -> PaletteJsonExportOutcome = { PaletteJsonExportOutcome.Exported }

    override suspend fun export(definition: PaletteDefinition): PaletteJsonExportOutcome {
        definitions += definition
        return handler(definition)
    }
}
