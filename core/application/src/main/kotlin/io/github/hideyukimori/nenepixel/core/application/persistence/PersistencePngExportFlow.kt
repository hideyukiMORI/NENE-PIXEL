package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.PngExportStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimePngExportOperations
import kotlinx.coroutines.CancellationException

internal class PersistencePngExportFlow(
    private val operations: RuntimePngExportOperations,
    private val exporter: PngExportPort,
    private val autosave: PersistenceAutosaveFlow,
) {
    suspend fun exportPng(): PersistenceRequestResult = autosave.retryAfterPublication { attempt() }

    private suspend fun attempt(): PersistenceRequestResult =
        when (val begun = operations.begin()) {
            is PngExportStart.Started -> export(begun)
            PngExportStart.Busy -> PersistenceRequestResult.Busy
            PngExportStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            PngExportStart.IdentityExhausted -> identityExhaustedResult()
            PngExportStart.NoVisibleLayer -> PersistenceRequestResult.NoVisibleLayer
        }

    private suspend fun export(start: PngExportStart.Started): PersistenceRequestResult =
        try {
            operations.complete(start.handle, exporter.export(start.capture))
        } catch (cancelled: CancellationException) {
            operations.cancel(start.handle)
            throw cancelled
        }
}
