package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.PngImportPickStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimePngImportOperations
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PersistencePngImportFlow(
    private val operations: RuntimePngImportOperations,
    private val picker: PngImportPort,
    private val autosave: PersistenceAutosaveFlow,
    private val conversionDispatcher: CoroutineDispatcher,
) {
    suspend fun pick(): PersistenceRequestResult = autosave.retryAfterPublication { attempt() }

    private suspend fun attempt(): PersistenceRequestResult =
        when (val start = operations.beginPick()) {
            is PngImportPickStart.Started -> pick(start)
            PngImportPickStart.Busy -> PersistenceRequestResult.Busy
            PngImportPickStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            PngImportPickStart.IdentityExhausted -> identityExhaustedResult()
            PngImportPickStart.PaletteSessionActive -> PersistenceRequestResult.PaletteSessionActive
        }

    private suspend fun pick(start: PngImportPickStart.Started): PersistenceRequestResult =
        try {
            val outcome = picker.pick()
            operations.completePick(start.handle, outcome, plan(outcome))
        } catch (cancelled: CancellationException) {
            operations.cancelPick(start.handle)
            throw cancelled
        }

    /** Plans the picked raster off the main thread while the lease is still held (ADR 0033). */
    private suspend fun plan(outcome: PngImportOutcome): PendingRasterImport? {
        val picked = outcome as? PngImportOutcome.Picked ?: return null
        val source = operations.planningSource()
        return withContext(conversionDispatcher) {
            PendingRasterImport.planned(picked.raster, source.canvas, source.definition)
        }
    }
}
