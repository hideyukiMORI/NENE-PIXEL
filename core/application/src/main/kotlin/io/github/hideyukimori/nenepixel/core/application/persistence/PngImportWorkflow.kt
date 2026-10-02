package io.github.hideyukimori.nenepixel.core.application.persistence

/**
 * Picks a PNG and, when it is read, offers the choice of how it is imported; opening the pending new-work
 * form is a work switch (ADR 0033).
 */
public class PngImportWorkflow internal constructor(
    private val flow: PersistencePngImportFlow,
    private val switch: PersistenceSwitchFlow,
) {
    public suspend fun pick(): PersistenceRequestResult = flow.pick()

    /**
     * Opens the pending new-work plan as a work switch. Unsaved changes return the same confirmation as any
     * other switch, continued with [EditorPersistenceWorkflow.confirm]; without a plan that can be chosen the
     * result is [PersistenceRequestResult.Stale] (ADR 0033).
     */
    public suspend fun openAsNewWork(): PersistenceRequestResult = switch.openImportedWork()
}
