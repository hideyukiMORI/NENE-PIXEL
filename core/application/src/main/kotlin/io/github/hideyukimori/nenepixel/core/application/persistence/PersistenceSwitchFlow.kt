package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.LoadTransportCompletion
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSwitchOperations
import io.github.hideyukimori.nenepixel.core.application.editor.SwitchContinuation
import io.github.hideyukimori.nenepixel.core.application.editor.SwitchStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PersistenceSwitchFlow(
    private val operations: RuntimeSwitchOperations,
    private val ports: PersistencePorts,
    private val autosave: PersistenceAutosaveFlow,
    private val conversionDispatcher: CoroutineDispatcher,
) {
    val legacy: PersistenceLegacyImportFlow = PersistenceLegacyImportFlow(operations, ports, conversionDispatcher)
    private val commit: PersistenceSwitchCommitFlow =
        PersistenceSwitchCommitFlow(operations, ports.recoveryRecord)

    suspend fun load(): PersistenceRequestResult = autosave.retryAfterPublication { applyStart(operations.beginLoad()) }

    suspend fun createNewDocument(request: NewDocumentRequestResult): PersistenceRequestResult =
        autosave.retryAfterPublication { applyStart(operations.beginNewDocument(request)) }

    suspend fun confirm(request: PersistenceConfirmationRequest): PersistenceRequestResult =
        applyContinuation(operations.confirmSwitch(request))

    fun cancel(operation: PersistenceOperationHandle): PersistenceCancellationResult =
        operations.cancelPersistence(operation)

    private suspend fun applyStart(start: SwitchStart): PersistenceRequestResult =
        when (start) {
            is SwitchStart.Load -> loadProject(start.handle)
            is SwitchStart.Ready -> commit.commit(start.handle)
            is SwitchStart.Confirmation -> PersistenceRequestResult.AwaitingConfirmation(start.request)
            is SwitchStart.Rejected -> PersistenceRequestResult.Rejected(start.rejection)
            SwitchStart.Busy -> PersistenceRequestResult.Busy
            SwitchStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            SwitchStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun applyContinuation(continuation: SwitchContinuation): PersistenceRequestResult =
        when (continuation) {
            is SwitchContinuation.Load -> {
                loadProject(continuation.handle)
            }

            is SwitchContinuation.Ready -> {
                commit.commit(continuation.handle)
            }

            is SwitchContinuation.Confirmation -> {
                PersistenceRequestResult.AwaitingConfirmation(continuation.request)
            }

            is SwitchContinuation.Result -> {
                continuation.result
            }

            is SwitchContinuation.LegacyPrepare -> {
                legacy.prepareAndCommit(continuation.permit)
            }

            SwitchContinuation.Stale -> {
                PersistenceRequestResult.Stale
            }

            SwitchContinuation.TooLate -> {
                PersistenceRequestResult.TooLate
            }
        }

    private suspend fun loadProject(handle: PersistenceOperationHandle): PersistenceRequestResult =
        try {
            val loaded = ports.projectStorage.load()
            val classified = withContext(conversionDispatcher) { loaded.classify() }
            applyLoadTransport(handle, operations.completeLoadTransport(handle, classified))
        } catch (cancelled: CancellationException) {
            operations.completeCancellation(handle)
            throw cancelled
        }

    private suspend fun applyLoadTransport(
        handle: PersistenceOperationHandle,
        completion: LoadTransportCompletion,
    ): PersistenceRequestResult =
        when (completion) {
            is LoadTransportCompletion.Ready -> {
                commit.commit(completion.handle)
            }

            is LoadTransportCompletion.Confirmation -> {
                PersistenceRequestResult.AwaitingConfirmation(completion.request)
            }

            is LoadTransportCompletion.Result -> {
                completion.result
            }

            LoadTransportCompletion.Stale -> {
                PersistenceRequestResult.Stale
            }
        }
}
