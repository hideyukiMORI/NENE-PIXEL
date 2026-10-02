package io.github.hideyukimori.nenepixel.core.application.persistence

/** Picks a PNG and, when it is read, offers the choice of how it is imported (ADR 0033). */
public class PngImportWorkflow internal constructor(
    private val flow: PersistencePngImportFlow,
) {
    public suspend fun pick(): PersistenceRequestResult = flow.pick()
}
