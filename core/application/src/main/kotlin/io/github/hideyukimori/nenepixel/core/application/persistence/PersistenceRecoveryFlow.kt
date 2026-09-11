package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.RecoveryDeclineStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeRecoveryOperations
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class PersistenceRecoveryFlow(
    private val operations: RuntimeRecoveryOperations,
    private val recoveryRecord: RecoveryRecordPort,
) {
    fun acceptRecovery(): PersistenceRequestResult = operations.adoptRecovery()

    suspend fun declineRecovery(): PersistenceRequestResult =
        when (val start = operations.beginDecline()) {
            is RecoveryDeclineStart.Started -> retire(start)
            is RecoveryDeclineStart.Result -> start.result
        }

    private suspend fun retire(start: RecoveryDeclineStart.Started): PersistenceRequestResult =
        withContext(NonCancellable) {
            operations.completeDecline(start.handle, recoveryRecord.retire(start.expected))
        }
}
