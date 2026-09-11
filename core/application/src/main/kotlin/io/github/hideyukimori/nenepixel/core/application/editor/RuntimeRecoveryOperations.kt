package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome

internal class RuntimeRecoveryOperations(
    private val runtime: EditorRuntime,
) {
    fun adoptRecovery(): PersistenceRequestResult =
        runtime.transact { transaction ->
            RecoveryAdoptionTransitions.begin(transaction.coordination, transaction.switchContext())
        }

    fun beginDecline(): RecoveryDeclineStart =
        runtime.transact { transaction -> RecoveryDeclineTransitions.begin(transaction.coordination) }

    fun completeDecline(
        handle: PersistenceOperationHandle,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            RecoveryDeclineTransitions.complete(transaction.coordination, handle, outcome)
        }
}
