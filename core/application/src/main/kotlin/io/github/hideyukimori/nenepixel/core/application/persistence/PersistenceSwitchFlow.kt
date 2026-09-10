package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.LoadTransportCompletion
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSwitchOperations
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSwitchPermit
import io.github.hideyukimori.nenepixel.core.application.editor.SwitchBegin
import io.github.hideyukimori.nenepixel.core.application.editor.SwitchContinuation
import io.github.hideyukimori.nenepixel.core.application.editor.SwitchStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class PersistenceSwitchFlow(
    private val operations: RuntimeSwitchOperations,
    private val projectStorage: ProjectStoragePort,
    private val recoveryRecord: RecoveryRecordPort,
) {
    suspend fun load(): PersistenceRequestResult = applyStart(operations.beginLoad())

    suspend fun createNewDocument(request: NewDocumentRequestResult): PersistenceRequestResult =
        applyStart(operations.beginNewDocument(request))

    suspend fun confirm(request: PersistenceConfirmationRequest): PersistenceRequestResult =
        applyContinuation(operations.confirmSwitch(request))

    fun cancel(operation: PersistenceOperationHandle): PersistenceCancellationResult =
        operations.cancelPersistence(operation)

    private suspend fun applyStart(start: SwitchStart): PersistenceRequestResult =
        when (start) {
            is SwitchStart.Load -> loadProject(start.handle)
            is SwitchStart.Ready -> commitSwitch(start.handle)
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
                commitSwitch(continuation.handle)
            }

            is SwitchContinuation.Confirmation -> {
                PersistenceRequestResult.AwaitingConfirmation(continuation.request)
            }

            is SwitchContinuation.Result -> {
                continuation.result
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
            applyLoadTransport(handle, operations.completeLoadTransport(handle, projectStorage.load()))
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
                commitSwitch(completion.handle)
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

    private suspend fun commitSwitch(handle: PersistenceOperationHandle): PersistenceRequestResult =
        withContext(NonCancellable) {
            when (val begin = operations.beginSwitch(handle)) {
                is SwitchBegin.Permit -> retireAndComplete(begin.permit)
                is SwitchBegin.Confirmation -> PersistenceRequestResult.AwaitingConfirmation(begin.request)
                is SwitchBegin.Result -> begin.result
            }
        }

    private suspend fun retireAndComplete(permit: RuntimeSwitchPermit): PersistenceRequestResult =
        operations.completeSwitch(permit, recoveryRecord.retire(permit.expected))
}
