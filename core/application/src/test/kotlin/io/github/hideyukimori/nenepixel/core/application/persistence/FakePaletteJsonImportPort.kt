package io.github.hideyukimori.nenepixel.core.application.persistence

internal class FakePaletteJsonImportPort : PaletteJsonImportPort {
    var calls: Int = 0
        private set
    var handler: suspend () -> PaletteJsonImportOutcome = { PaletteJsonImportOutcome.Cancelled }

    override suspend fun import(): PaletteJsonImportOutcome {
        calls += 1
        return handler()
    }
}
