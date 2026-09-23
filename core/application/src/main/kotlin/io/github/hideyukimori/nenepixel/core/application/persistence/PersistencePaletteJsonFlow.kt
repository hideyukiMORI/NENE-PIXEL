package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.PaletteJsonExportStart
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteJsonImportStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimePaletteJsonOperations
import kotlinx.coroutines.CancellationException

internal class PersistencePaletteJsonFlow(
    private val operations: RuntimePaletteJsonOperations,
    private val exporter: PaletteJsonExportPort,
    private val importer: PaletteJsonImportPort,
    private val autosave: PersistenceAutosaveFlow,
) {
    suspend fun export(): PersistenceRequestResult = autosave.retryAfterPublication { attempt() }

    suspend fun import(): PersistenceRequestResult = autosave.retryAfterPublication { attemptImport() }

    private suspend fun attempt(): PersistenceRequestResult =
        when (val start = operations.begin()) {
            is PaletteJsonExportStart.Started -> export(start)
            PaletteJsonExportStart.Busy -> PersistenceRequestResult.Busy
            PaletteJsonExportStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            PaletteJsonExportStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun export(start: PaletteJsonExportStart.Started): PersistenceRequestResult =
        try {
            operations.complete(start.handle, exporter.export(start.definition))
        } catch (cancelled: CancellationException) {
            operations.cancel(start.handle)
            throw cancelled
        }

    private suspend fun attemptImport(): PersistenceRequestResult =
        when (val start = operations.beginImport()) {
            is PaletteJsonImportStart.Started -> import(start)
            PaletteJsonImportStart.Busy -> PersistenceRequestResult.Busy
            PaletteJsonImportStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            PaletteJsonImportStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun import(start: PaletteJsonImportStart.Started): PersistenceRequestResult =
        try {
            operations.completeImport(start.handle, importer.import())
        } catch (cancelled: CancellationException) {
            operations.cancelImport(start.handle)
            throw cancelled
        }
}
