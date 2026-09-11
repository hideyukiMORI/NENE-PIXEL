package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome

internal class RuntimeSaveOperations(
    private val runtime: EditorRuntime,
) {
    fun beginRecoveryInspection(): RecoveryInspectionStart =
        runtime.transact { transaction -> RecoveryInspectionTransitions.begin(transaction.coordination) }

    fun completeRecoveryInspection(outcome: RecoveryInspection): RecoveryInitializationResult =
        runtime.transact { transaction -> RecoveryInspectionTransitions.complete(transaction.coordination, outcome) }

    fun beginSave(): DocumentOutputStart =
        runtime.transact { transaction ->
            SaveTransitions.begin(
                transaction.coordination,
                transaction.documentState(),
                transaction.historyPosition(),
            )
        }

    fun completeSaveTransport(
        handle: PersistenceOperationHandle,
        outcome: ProjectSaveOutcome,
    ): SaveTransportCompletion =
        runtime.transact { transaction ->
            SaveTransitions.completeTransport(
                transaction.coordination,
                handle,
                outcome,
                transaction.documentId(),
            )
        }

    fun completeSaveCleanup(
        handle: PersistenceOperationHandle,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            SaveCleanupTransitions.complete(transaction.coordination, handle, outcome)
        }

    fun completeCancellation(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }
}
