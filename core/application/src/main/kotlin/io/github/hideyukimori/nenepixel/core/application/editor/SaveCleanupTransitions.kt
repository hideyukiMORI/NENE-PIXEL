package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryCleanupOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason

internal object SaveCleanupTransitions {
    fun complete(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceTransition<PersistenceRequestResult> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Save
        val matches = operation != null && operation.handle == handle && operation.phase == SavePhase.Cleanup
        return if (matches) {
            finish(coordination, outcome)
        } else {
            PersistenceTransition(coordination, PersistenceRequestResult.Stale)
        }
    }

    private fun finish(
        coordination: PersistenceCoordination,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceTransition<PersistenceRequestResult> =
        when (outcome) {
            is RecoveryRetirementOutcome.Retired -> {
                val state = RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Present(outcome.generation))
                saved(coordination.withRecovery(state), RecoveryCleanupOutcome.Retired(outcome.generation))
            }

            RecoveryRetirementOutcome.Stale -> {
                val state = RuntimeRecoveryState.Unknown(RecoveryUnavailableReason.Stale)
                saved(coordination.withRecovery(state), RecoveryCleanupOutcome.Stale)
            }

            RecoveryRetirementOutcome.GenerationExhausted -> {
                saved(coordination, RecoveryCleanupOutcome.GenerationExhausted)
            }

            is RecoveryRetirementOutcome.Failed -> {
                saved(coordination, RecoveryCleanupOutcome.Failed(outcome.failure, outcome.rollback))
            }

            is RecoveryRetirementOutcome.Uncertain -> {
                uncertain(coordination, outcome)
            }
        }

    private fun uncertain(
        coordination: PersistenceCoordination,
        outcome: RecoveryRetirementOutcome.Uncertain,
    ): PersistenceTransition<PersistenceRequestResult> {
        val reason = RecoveryUnavailableReason.RetirementUncertain(outcome.failure, outcome.rollback)
        return saved(
            coordination.withRecovery(RuntimeRecoveryState.Unknown(reason)),
            RecoveryCleanupOutcome.Uncertain(outcome.failure, outcome.rollback),
        )
    }

    private fun saved(
        coordination: PersistenceCoordination,
        cleanup: RecoveryCleanupOutcome,
    ): PersistenceTransition<PersistenceRequestResult> = coordination.completed(PersistenceLastOutcome.Saved(cleanup))
}
