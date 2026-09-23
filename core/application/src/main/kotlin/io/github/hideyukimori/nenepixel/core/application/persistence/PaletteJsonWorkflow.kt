package io.github.hideyukimori.nenepixel.core.application.persistence

public class PaletteJsonWorkflow internal constructor(
    private val flow: PersistencePaletteJsonFlow,
) {
    public suspend fun export(): PersistenceRequestResult = flow.export()

    public suspend fun import(): PersistenceRequestResult = flow.import()
}
