package io.github.hideyukimori.nenepixel.core.application.persistence

internal class FakePngImportPort : PngImportPort {
    var calls: Int = 0
        private set
    var handler: suspend () -> PngImportOutcome = { PngImportOutcome.Cancelled }

    override suspend fun pick(): PngImportOutcome {
        calls += 1
        return handler()
    }
}
