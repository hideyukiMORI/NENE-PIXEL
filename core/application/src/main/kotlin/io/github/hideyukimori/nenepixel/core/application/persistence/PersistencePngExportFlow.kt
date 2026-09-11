package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentOutputStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimePngExportOperations
import kotlinx.coroutines.CancellationException

internal class PersistencePngExportFlow(
    private val operations: RuntimePngExportOperations,
    private val exporter: PngExportPort,
    private val autosave: PersistenceAutosaveFlow,
) {
    suspend fun exportPng(): PersistenceRequestResult = autosave.retryAfterPublication { attempt() }

    private suspend fun attempt(): PersistenceRequestResult =
        when (val start = operations.begin()) {
            is DocumentOutputStart.Started -> export(start)
            DocumentOutputStart.Busy -> PersistenceRequestResult.Busy
            DocumentOutputStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            DocumentOutputStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun export(start: DocumentOutputStart.Started): PersistenceRequestResult =
        try {
            operations.complete(start.handle, exporter.export(start.document))
        } catch (cancelled: CancellationException) {
            operations.cancel(start.handle)
            throw cancelled
        }
}
