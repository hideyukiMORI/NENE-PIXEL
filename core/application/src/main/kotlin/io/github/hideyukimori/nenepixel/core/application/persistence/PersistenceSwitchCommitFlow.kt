package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSwitchOperations
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSwitchPermit
import io.github.hideyukimori.nenepixel.core.application.editor.SwitchBegin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class PersistenceSwitchCommitFlow(
    private val operations: RuntimeSwitchOperations,
    private val recoveryRecord: RecoveryRecordPort,
) {
    suspend fun commit(handle: PersistenceOperationHandle): PersistenceRequestResult =
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
