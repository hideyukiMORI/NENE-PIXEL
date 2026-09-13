package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason

internal sealed interface RecoveryDeclineStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val expected: ExpectedRecoveryLineage,
    ) : RecoveryDeclineStart

    data class Result(
        val result: PersistenceRequestResult,
    ) : RecoveryDeclineStart

    data class LegacyRequired(
        val operation: PersistenceOperationHandle,
    ) : RecoveryDeclineStart
}

internal object RecoveryDeclineTransitions {
    fun begin(
        coordination: PersistenceCoordination,
        context: SwitchContext,
    ): PersistenceTransition<RecoveryDeclineStart> {
        if (coordination.recoveryState is RuntimeRecoveryState.LegacyCandidate) {
            val transition =
                LegacyImportTransitions.startRecovery(
                    coordination,
                    context,
                    LegacyImportPurpose.DeclineRecovery,
                )
            val result = transition.result
            val start =
                if (result is PersistenceRequestResult.LegacyConversionRequired) {
                    RecoveryDeclineStart.LegacyRequired(result.operation)
                } else {
                    RecoveryDeclineStart.Result(result)
                }
            return PersistenceTransition(transition.next, start, transition.effect)
        }
        val candidate = coordination.recoveryState as? RuntimeRecoveryState.Candidate
        return when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, RecoveryDeclineStart.Result(PersistenceRequestResult.Busy))
            }

            candidate == null -> {
                PersistenceTransition(coordination, RecoveryDeclineStart.Result(PersistenceRequestResult.Stale))
            }

            else -> {
                start(coordination, ExpectedRecoveryLineage.Present(candidate.generation))
            }
        }
    }

    fun beginVerifiedLegacy(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
    ): PersistenceTransition<RecoveryDeclineStart> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        val recovery = coordination.recoveryState as? RuntimeRecoveryState.LegacyCandidate
        val origin = operation?.origin
        return when {
            operation == null || operation.handle != handle -> {
                PersistenceTransition(coordination, RecoveryDeclineStart.Result(PersistenceRequestResult.Stale))
            }

            operation.purpose != LegacyImportPurpose.DeclineRecovery ||
                operation.phase !is LegacyImportPhase.Required -> {
                PersistenceTransition(coordination, RecoveryDeclineStart.Result(PersistenceRequestResult.TooLate))
            }

            !operation.originalCopyVerified -> {
                PersistenceTransition(
                    coordination,
                    RecoveryDeclineStart.Result(PersistenceRequestResult.OriginalCopyRequired),
                )
            }

            recovery == null || origin !is LegacyImportSourceOrigin.Recovery ||
                origin.generation != recovery.generation ||
                operation.candidate.source !== recovery.candidate.source -> {
                PersistenceTransition(coordination, RecoveryDeclineStart.Result(PersistenceRequestResult.Stale))
            }

            else -> {
                val expected = ExpectedRecoveryLineage.Present(recovery.generation)
                PersistenceTransition(
                    coordination.withActive(ActivePersistenceOperation.RecoveryDecline(handle, expected)),
                    RecoveryDeclineStart.Started(handle, expected),
                )
            }
        }
    }

    private fun start(
        coordination: PersistenceCoordination,
        expected: ExpectedRecoveryLineage,
    ): PersistenceTransition<RecoveryDeclineStart> =
        when (val creation = coordination.nextOperationHandle()) {
            is OperationHandleCreation.Created -> {
                PersistenceTransition(
                    creation.next.withActive(
                        ActivePersistenceOperation.RecoveryDecline(creation.handle, expected),
                    ),
                    RecoveryDeclineStart.Started(creation.handle, expected),
                )
            }

            OperationHandleCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, RecoveryDeclineStart.Result(exhausted.result))
            }
        }

    fun complete(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceTransition<PersistenceRequestResult> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.RecoveryDecline
        return if (operation == null || operation.handle != handle) {
            PersistenceTransition(coordination, PersistenceRequestResult.Stale)
        } else {
            finish(coordination, outcome)
        }
    }

    private fun finish(
        coordination: PersistenceCoordination,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceTransition<PersistenceRequestResult> =
        when (outcome) {
            is RecoveryRetirementOutcome.Retired -> {
                coordination
                    .withRecovery(RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Present(outcome.generation)))
                    .completed(PersistenceLastOutcome.RecoveryDeclined)
            }

            RecoveryRetirementOutcome.Stale -> {
                coordination
                    .withRecovery(RuntimeRecoveryState.Unknown(RecoveryUnavailableReason.Stale))
                    .completed(PersistenceLastOutcome.Failed(PersistenceFailure.RecoveryLineageChanged))
            }

            RecoveryRetirementOutcome.GenerationExhausted -> {
                coordination.completed(
                    PersistenceLastOutcome.Failed(PersistenceFailure.RecoveryGenerationExhausted),
                )
            }

            is RecoveryRetirementOutcome.Failed -> {
                coordination.completed(
                    PersistenceLastOutcome.Failed(
                        PersistenceFailure.RecoveryRetirementFailed(outcome.failure, outcome.rollback),
                    ),
                )
            }

            is RecoveryRetirementOutcome.Uncertain -> {
                val reason = RecoveryUnavailableReason.RetirementUncertain(outcome.failure, outcome.rollback)
                coordination
                    .withRecovery(RuntimeRecoveryState.Unknown(reason))
                    .completed(
                        PersistenceLastOutcome.Failed(
                            PersistenceFailure.RecoveryRetirementUncertain(outcome.failure, outcome.rollback),
                        ),
                    )
            }
        }
}
