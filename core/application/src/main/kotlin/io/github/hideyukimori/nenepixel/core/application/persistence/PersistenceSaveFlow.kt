package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.RecoveryInspectionStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSaveOperations
import io.github.hideyukimori.nenepixel.core.application.editor.SaveStart
import io.github.hideyukimori.nenepixel.core.application.editor.SaveTransportCompletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class PersistenceSaveFlow(
    private val operations: RuntimeSaveOperations,
    private val projectStorage: ProjectStoragePort,
    private val recoveryRecord: RecoveryRecordPort,
    private val autosave: PersistenceAutosaveFlow,
) {
    suspend fun initializeRecovery(): RecoveryInitializationResult =
        when (operations.beginRecoveryInspection()) {
            RecoveryInspectionStart.Started -> inspectRecovery()
            RecoveryInspectionStart.AlreadyReady -> RecoveryInitializationResult.AlreadyReady
            RecoveryInspectionStart.Busy -> RecoveryInitializationResult.Busy
        }

    suspend fun saveAs(): PersistenceRequestResult = autosave.retryAfterPublication { attemptSave() }

    private suspend fun attemptSave(): PersistenceRequestResult =
        when (val start = operations.beginSave()) {
            is SaveStart.Started -> save(start)
            SaveStart.Busy -> PersistenceRequestResult.Busy
            SaveStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            SaveStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun inspectRecovery(): RecoveryInitializationResult =
        try {
            operations.completeRecoveryInspection(recoveryRecord.inspect())
        } catch (cancelled: CancellationException) {
            operations.completeRecoveryInspection(
                RecoveryInspection.Failed(RecoveryInspectionFailure.READ_FAILED),
            )
            throw cancelled
        }

    private suspend fun save(start: SaveStart.Started): PersistenceRequestResult =
        try {
            applyTransport(start, projectStorage.save(start.document))
        } catch (cancelled: CancellationException) {
            operations.completeCancellation(start.handle)
            throw cancelled
        }

    private suspend fun applyTransport(
        start: SaveStart.Started,
        outcome: ProjectSaveOutcome,
    ): PersistenceRequestResult =
        when (val completion = operations.completeSaveTransport(start.handle, outcome)) {
            is SaveTransportCompletion.Cleanup -> finishSaveCleanup(completion)
            is SaveTransportCompletion.Result -> completion.result
            SaveTransportCompletion.Stale -> PersistenceRequestResult.Stale
        }

    private suspend fun finishSaveCleanup(completion: SaveTransportCompletion.Cleanup): PersistenceRequestResult =
        withContext(NonCancellable) {
            operations.completeSaveCleanup(completion.handle, recoveryRecord.retire(completion.expected))
        }
}
