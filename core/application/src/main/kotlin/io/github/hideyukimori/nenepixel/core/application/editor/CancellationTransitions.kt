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
            is ActivePersistenceOperation.Save -> {
                PersistenceTransition(
                    coordination.withActive(active.copy(phase = SavePhase.Cancelling)),
                    PersistenceCancellationResult.CancellationStarted,
                )
            }

            is ActivePersistenceOperation.Switch.Loading -> {
                PersistenceTransition(
                    coordination.withActive(ActivePersistenceOperation.Switch.Cancelling(active.handle)),
                    PersistenceCancellationResult.CancellationStarted,
                )
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

            is ActivePersistenceOperation.Switch.Cancelling -> {
                PersistenceTransition(coordination, PersistenceCancellationResult.Stale)
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

            active is ActivePersistenceOperation.Save && active.phase != SavePhase.Cleanup -> {
                coordination.cancelled()
            }

            active is ActivePersistenceOperation.Switch.Loading ||
                active is ActivePersistenceOperation.Switch.Cancelling -> {
                coordination.cancelled()
            }

            else -> {
                PersistenceTransition(coordination, PersistenceRequestResult.TooLate)
            }
        }
    }
}
