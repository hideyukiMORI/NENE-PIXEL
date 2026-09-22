package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentOutputStart
import io.github.hideyukimori.nenepixel.core.application.editor.RecoveryInspectionStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSaveOperations
import io.github.hideyukimori.nenepixel.core.application.editor.SaveTransportCompletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class PersistenceSaveFlow(
    private val operations: RuntimeSaveOperations,
    private val ports: PersistencePorts,
    private val autosave: PersistenceAutosaveFlow,
    private val conversionDispatcher: CoroutineDispatcher,
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
            is DocumentOutputStart.Started -> save(start)
            DocumentOutputStart.Busy -> PersistenceRequestResult.Busy
            DocumentOutputStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            DocumentOutputStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun inspectRecovery(): RecoveryInitializationResult =
        try {
            val inspection = ports.recoveryRecord.inspect()
            operations.completeRecoveryInspection(withContext(conversionDispatcher) { inspection.classify() })
        } catch (cancelled: CancellationException) {
            operations.completeRecoveryInspection(
                ClassifiedRecoveryInspection.Failed(RecoveryInspectionFailure.READ_FAILED),
            )
            throw cancelled
        }

    private suspend fun save(start: DocumentOutputStart.Started): PersistenceRequestResult =
        try {
            applyTransport(start, ports.projectStorage.save(start.document))
        } catch (cancelled: CancellationException) {
            operations.completeCancellation(start.handle)
            throw cancelled
        }

    private suspend fun applyTransport(
        start: DocumentOutputStart.Started,
        outcome: ProjectSaveOutcome,
    ): PersistenceRequestResult =
        when (val completion = operations.completeSaveTransport(start.handle, outcome)) {
            is SaveTransportCompletion.Cleanup -> finishSaveCleanup(completion)
            is SaveTransportCompletion.Result -> completion.result
            SaveTransportCompletion.Stale -> PersistenceRequestResult.Stale
        }

    private suspend fun finishSaveCleanup(completion: SaveTransportCompletion.Cleanup): PersistenceRequestResult =
        withContext(NonCancellable) {
            operations.completeSaveCleanup(completion.handle, ports.recoveryRecord.retire(completion.expected))
        }
}
