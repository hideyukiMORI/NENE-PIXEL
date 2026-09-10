package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceCancellationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome

internal class RuntimeSwitchOperations(
    private val runtime: EditorRuntime,
) {
    fun beginLoad(): SwitchStart =
        runtime.transact { transaction ->
            SwitchStartTransitions.beginLoad(transaction.coordination, transaction.switchContext())
        }

    fun beginNewDocument(request: NewDocumentRequestResult): SwitchStart =
        runtime.transact { transaction ->
            SwitchStartTransitions.beginNewDocument(
                transaction.coordination,
                request,
                transaction.switchContext(),
            )
        }

    fun completeLoadTransport(
        handle: PersistenceOperationHandle,
        outcome: ProjectLoadOutcome,
    ): LoadTransportCompletion =
        runtime.transact { transaction ->
            LoadTransportTransitions.complete(
                transaction.coordination,
                handle,
                outcome,
                transaction.switchContext(),
            )
        }

    fun confirmSwitch(request: PersistenceConfirmationRequest): SwitchContinuation =
        runtime.transact { transaction ->
            SwitchConfirmationTransitions.confirm(
                transaction.coordination,
                request,
                transaction.switchContext(),
            )
        }

    fun beginSwitch(handle: PersistenceOperationHandle): SwitchBegin =
        runtime.transact { transaction ->
            SwitchCommitTransitions.begin(transaction.coordination, handle, transaction.switchContext())
        }

    fun completeSwitch(
        permit: RuntimeSwitchPermit,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            SwitchCommitTransitions.complete(transaction.coordination, permit, outcome)
        }

    fun cancelPersistence(handle: PersistenceOperationHandle): PersistenceCancellationResult =
        runtime.transact { transaction -> CancellationTransitions.cancel(transaction.coordination, handle) }

    fun completeCancellation(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }
}
