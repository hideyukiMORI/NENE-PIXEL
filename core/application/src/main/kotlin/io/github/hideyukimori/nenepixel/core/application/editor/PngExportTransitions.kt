package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal object PngExportTransitions {
    fun begin(
        coordination: PersistenceCoordination,
        document: DocumentState,
    ): PersistenceTransition<DocumentOutputStart> =
        when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, DocumentOutputStart.Busy)
            }

            coordination.recoveryState is RuntimeRecoveryState.Initializing -> {
                PersistenceTransition(coordination, DocumentOutputStart.RecoveryUnavailable)
            }

            else -> {
                start(coordination, document)
            }
        }

    private fun start(
        coordination: PersistenceCoordination,
        document: DocumentState,
    ): PersistenceTransition<DocumentOutputStart> =
        when (val creation = coordination.nextOperationHandle()) {
            OperationHandleCreation.Exhausted -> {
                PersistenceTransition(coordination.identityExhausted(), DocumentOutputStart.IdentityExhausted)
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
                    DocumentOutputStart.Started(creation.handle, document),
                )
            }
        }

    fun complete(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        outcome: PngExportOutcome,
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
                val last = outcome.toLastOutcome()
                PersistenceTransition(coordination.finished(last), PersistenceRequestResult.Completed(last))
            }
        }
    }

    private fun PngExportOutcome.toLastOutcome(): PersistenceLastOutcome =
        when (this) {
            PngExportOutcome.Exported -> PersistenceLastOutcome.PngExported
            PngExportOutcome.Cancelled -> PersistenceLastOutcome.Cancelled
            is PngExportOutcome.Failed -> PersistenceLastOutcome.Failed(PersistenceFailure.PngExport(failure, cleanup))
        }
}
