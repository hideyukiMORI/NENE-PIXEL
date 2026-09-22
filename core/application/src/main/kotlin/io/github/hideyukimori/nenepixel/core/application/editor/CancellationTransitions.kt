package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceCancellationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult

internal object CancellationTransitions {
    fun cancel(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
    ): PersistenceTransition<PersistenceCancellationResult> {
        val active = coordination.activeOperation
        return when {
            active == null -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.Idle)
            }

            active.handle != handle -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.Stale)
            }

            active.isSwitching() || active.isSaveCleanup() -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.TooLate)
            }

            active.isCancelling() -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.Stale)
            }

            else -> {
                start(coordination, active)
            }
        }
    }

    private fun start(
        coordination: PersistenceCoordination,
        active: ActivePersistenceOperation,
    ): PersistenceTransition<PersistenceCancellationResult> =
        when (active) {
            is ActivePersistenceOperation.Export -> {
                PersistenceTransition(
                    coordination.withActive(active.copy(phase = ExportPhase.Cancelling)),
                    PersistenceCancellationResult.CancellationStarted,
                )
            }

            is ActivePersistenceOperation.Save -> {
                PersistenceTransition(
                    coordination.withActive(active.copy(phase = SavePhase.Cancelling)),
                    PersistenceCancellationResult.CancellationStarted,
                )
            }

            is ActivePersistenceOperation.Switch.Loading -> {
                cancelLoading(coordination, active)
            }

            is ActivePersistenceOperation.Switch.LegacyImport -> {
                cancelLegacyImport(coordination, active)
            }

            is ActivePersistenceOperation.Switch.Confirming,
            is ActivePersistenceOperation.Switch.Ready,
            -> {
                PersistenceTransition(
                    coordination.finished(PersistenceLastOutcome.Cancelled),
                    PersistenceCancellationResult.Cancelled,
                )
            }

            is ActivePersistenceOperation.Switch.Switching -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.TooLate)
            }

            is ActivePersistenceOperation.Autosave,
            is ActivePersistenceOperation.RecoveryDecline,
            -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.TooLate)
            }

            is ActivePersistenceOperation.Switch.Cancelling -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.Stale)
            }
        }

    private fun cancelLoading(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Loading,
    ): PersistenceTransition<PersistenceCancellationResult> =
        PersistenceTransition(
            coordination.withActive(ActivePersistenceOperation.Switch.Cancelling(operation.handle)),
            PersistenceCancellationResult.CancellationStarted,
        )

    private fun cancelLegacyImport(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.LegacyImport,
    ): PersistenceTransition<PersistenceCancellationResult> {
        val phase = operation.phase
        return when (phase) {
            is LegacyImportPhase.Required -> {
                PersistenceTransition(
                    coordination.finished(PersistenceLastOutcome.Cancelled),
                    PersistenceCancellationResult.Cancelled,
                )
            }

            is LegacyImportPhase.Copying,
            is LegacyImportPhase.Reducing,
            is LegacyImportPhase.Preparing,
            -> {
                val preview =
                    when (phase) {
                        is LegacyImportPhase.Required -> phase.preview
                        is LegacyImportPhase.Copying -> phase.previousPreview
                        is LegacyImportPhase.Preparing -> phase.preview
                        is LegacyImportPhase.Reducing -> null
                        is LegacyImportPhase.Cancelling -> phase.preview
                    }
                PersistenceTransition(
                    coordination.withActive(operation.withPhase(LegacyImportPhase.Cancelling(preview))),
                    PersistenceCancellationResult.CancellationStarted,
                )
            }

            is LegacyImportPhase.Cancelling -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.Stale)
            }
        }
    }

    fun completeCancellation(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
    ): PersistenceTransition<PersistenceRequestResult> {
        val active = coordination.activeOperation
        return when {
            active == null || active.handle != handle -> {
                PersistenceTransition(coordination, PersistenceRequestResult.Stale)
            }

            active is ActivePersistenceOperation.Export -> {
                coordination.cancelled()
            }

            active is ActivePersistenceOperation.Save && active.phase != SavePhase.Cleanup -> {
                coordination.cancelled()
            }

            active is ActivePersistenceOperation.Switch.Loading ||
                active is ActivePersistenceOperation.Switch.Cancelling ||
                (
                    active is ActivePersistenceOperation.Switch.LegacyImport &&
                        active.phase is LegacyImportPhase.Cancelling
                ) -> {
                coordination.cancelled()
            }

            else -> {
                PersistenceTransition(coordination, PersistenceRequestResult.TooLate)
            }
        }
    }
}
