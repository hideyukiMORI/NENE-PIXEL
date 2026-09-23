package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult

internal object DocumentOutputTransitions {
    fun begin(coordination: PersistenceCoordination): PersistenceTransition<DocumentOutputLease> =
        when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, DocumentOutputLease.Busy)
            }

            coordination.recoveryState is RuntimeRecoveryState.Initializing -> {
                PersistenceTransition(coordination, DocumentOutputLease.RecoveryUnavailable)
            }

            else -> {
                start(coordination)
            }
        }

    private fun start(coordination: PersistenceCoordination): PersistenceTransition<DocumentOutputLease> =
        when (val creation = coordination.nextOperationHandle()) {
            OperationHandleCreation.Exhausted -> {
                PersistenceTransition(coordination.identityExhausted(), DocumentOutputLease.IdentityExhausted)
            }

            is OperationHandleCreation.Created -> {
                val operation =
                    ActivePersistenceOperation.Export(
                        creation.handle,
                        creation.next.runtimeGeneration,
                        ExportPhase.Transport,
                    )
                PersistenceTransition(
                    creation.next.withActive(operation),
                    DocumentOutputLease.Started(creation.handle),
                )
            }
        }

    fun complete(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        last: PersistenceLastOutcome,
    ): PersistenceTransition<PersistenceRequestResult> {
        val active = coordination.activeOperation as? ActivePersistenceOperation.Export
        return when {
            active == null || active.handle != handle -> {
                PersistenceTransition(coordination, PersistenceRequestResult.Stale)
            }

            active.runtimeGeneration != coordination.runtimeGeneration -> {
                PersistenceTransition(coordination.withActive(null), PersistenceRequestResult.Stale)
            }

            active.phase == ExportPhase.Cancelling -> {
                coordination.cancelled()
            }

            else -> {
                PersistenceTransition(coordination.finished(last), PersistenceRequestResult.Completed(last))
            }
        }
    }
}
