package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason

internal data class RuntimeSwitchPermit(
    val handle: PersistenceOperationHandle,
    val expected: ExpectedRecoveryLineage,
    val candidate: RuntimeOwners,
)

internal sealed interface SwitchBegin {
    data class Permit(
        val permit: RuntimeSwitchPermit,
    ) : SwitchBegin

    data class Confirmation(
        val request: PersistenceConfirmationRequest,
    ) : SwitchBegin

    data class Result(
        val result: PersistenceRequestResult,
    ) : SwitchBegin
}

internal object SwitchCommitTransitions {
    fun begin(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        context: SwitchContext,
    ): PersistenceTransition<SwitchBegin> {
        val operation = coordination.activeOperation
        return when {
            operation == null || operation.handle != handle -> {
                PersistenceTransition(coordination, SwitchBegin.Result(PersistenceRequestResult.Stale))
            }

            operation is ActivePersistenceOperation.Switch.Cancelling -> {
                PersistenceTransition(coordination, SwitchBegin.Result(PersistenceRequestResult.Stale))
            }

            operation is ActivePersistenceOperation.Switch.Switching -> {
                PersistenceTransition(coordination, SwitchBegin.Result(PersistenceRequestResult.TooLate))
            }

            operation !is ActivePersistenceOperation.Switch.Ready -> {
                PersistenceTransition(coordination, SwitchBegin.Result(PersistenceRequestResult.Stale))
            }

            else -> {
                prepare(coordination, operation, context)
            }
        }
    }

    private fun prepare(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Ready,
        context: SwitchContext,
    ): PersistenceTransition<SwitchBegin> =
        when {
            context.source != operation.consentedSource -> {
                recheckSource(coordination, operation, context)
            }

            coordination.runtimeGeneration == Long.MAX_VALUE -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, SwitchBegin.Result(exhausted.result))
            }

            else -> {
                enterSwitching(coordination, operation)
            }
        }

    private fun enterSwitching(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Ready,
    ): PersistenceTransition<SwitchBegin> =
        when (val lineage = coordination.recoveryState.expectedLineage()) {
            is ExpectedLineageResult.Available -> {
                val switching =
                    ActivePersistenceOperation.Switch.Switching(
                        operation.handle,
                        operation.candidate,
                        operation.kind,
                    )
                PersistenceTransition(
                    coordination.withActive(switching),
                    SwitchBegin.Permit(
                        RuntimeSwitchPermit(operation.handle, lineage.expected, operation.candidate),
                    ),
                    RuntimeOwnerEffect.CancelPreview,
                )
            }

            ExpectedLineageResult.Unavailable -> {
                PersistenceTransition(
                    coordination.withActive(null),
                    SwitchBegin.Result(PersistenceRequestResult.RecoveryUnavailable),
                )
            }
        }

    private fun recheckSource(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Ready,
        context: SwitchContext,
    ): PersistenceTransition<SwitchBegin> {
        val reason = ConfirmationPolicy.sourceChangedReason(coordination)
        return when (val creation = coordination.nextConfirmation(operation.handle, reason)) {
            is ConfirmationCreation.Created -> {
                val confirming =
                    ActivePersistenceOperation.Switch.Confirming(
                        operation.handle,
                        context.source,
                        PendingSwitch.Prepared(operation.candidate, operation.kind),
                        creation.request,
                    )
                PersistenceTransition(
                    creation.next.withActive(confirming),
                    SwitchBegin.Confirmation(creation.request),
                )
            }

            ConfirmationCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, SwitchBegin.Result(exhausted.result))
            }
        }
    }

    fun complete(
        coordination: PersistenceCoordination,
        permit: RuntimeSwitchPermit,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceTransition<PersistenceRequestResult> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.Switching
        return if (operation == null || operation.handle != permit.handle) {
            PersistenceTransition(coordination, PersistenceRequestResult.Stale)
        } else {
            finish(coordination, operation, permit, outcome)
        }
    }

    private fun finish(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Switching,
        permit: RuntimeSwitchPermit,
        outcome: RecoveryRetirementOutcome,
    ): PersistenceTransition<PersistenceRequestResult> =
        when (outcome) {
            is RecoveryRetirementOutcome.Retired -> {
                install(coordination, operation.kind, permit, outcome)
            }

            RecoveryRetirementOutcome.Stale -> {
                val state = RuntimeRecoveryState.Unknown(RecoveryUnavailableReason.Stale)
                coordination
                    .withRecovery(state)
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
                failUncertain(coordination, outcome)
            }
        }

    private fun install(
        coordination: PersistenceCoordination,
        kind: SwitchKind,
        permit: RuntimeSwitchPermit,
        outcome: RecoveryRetirementOutcome.Retired,
    ): PersistenceTransition<PersistenceRequestResult> {
        val lastOutcome =
            when (kind) {
                SwitchKind.Loaded -> PersistenceLastOutcome.Loaded
                SwitchKind.NewDocument -> PersistenceLastOutcome.NewDocumentCreated
            }
        val next =
            coordination
                .advanceRuntimeGeneration()
                .withRecovery(RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Present(outcome.generation)))
                .finished(lastOutcome)
        return PersistenceTransition(
            next,
            PersistenceRequestResult.Completed(lastOutcome),
            RuntimeOwnerEffect.ReplaceOwners(permit.candidate),
        )
    }

    private fun failUncertain(
        coordination: PersistenceCoordination,
        outcome: RecoveryRetirementOutcome.Uncertain,
    ): PersistenceTransition<PersistenceRequestResult> {
        val reason = RecoveryUnavailableReason.RetirementUncertain(outcome.failure, outcome.rollback)
        return coordination
            .withRecovery(RuntimeRecoveryState.Unknown(reason))
            .completed(
                PersistenceLastOutcome.Failed(
                    PersistenceFailure.RecoveryRetirementUncertain(outcome.failure, outcome.rollback),
                ),
            )
    }
}
