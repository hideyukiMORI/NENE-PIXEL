package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationReason
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration

internal object RecoveryAdoptionTransitions {
    fun begin(
        coordination: PersistenceCoordination,
        context: SwitchContext,
    ): PersistenceTransition<PersistenceRequestResult> {
        val candidate = coordination.recoveryState as? RuntimeRecoveryState.Candidate
        return when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, PersistenceRequestResult.Busy)
            }

            candidate == null -> {
                PersistenceTransition(coordination, PersistenceRequestResult.Stale)
            }

            else -> {
                start(coordination, context.loadedOwners(candidate.document).asUnsaved(), context)
            }
        }
    }

    private fun start(
        coordination: PersistenceCoordination,
        candidate: RuntimeOwners,
        context: SwitchContext,
    ): PersistenceTransition<PersistenceRequestResult> =
        if (context.dirty) {
            confirm(coordination, candidate, context.source)
        } else {
            adopt(coordination, candidate)
        }

    private fun confirm(
        coordination: PersistenceCoordination,
        candidate: RuntimeOwners,
        source: RuntimeSourceToken,
    ): PersistenceTransition<PersistenceRequestResult> =
        when (val creation = coordination.nextOperationHandle()) {
            is OperationHandleCreation.Created -> {
                awaitConfirmation(creation, candidate, source)
            }

            OperationHandleCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, exhausted.result)
            }
        }

    private fun awaitConfirmation(
        creation: OperationHandleCreation.Created,
        candidate: RuntimeOwners,
        source: RuntimeSourceToken,
    ): PersistenceTransition<PersistenceRequestResult> {
        val reason = PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES
        return when (val confirmation = creation.next.nextConfirmation(creation.handle, reason)) {
            is ConfirmationCreation.Created -> {
                val operation =
                    ActivePersistenceOperation.Switch.Confirming(
                        creation.handle,
                        source,
                        PendingSwitch.Recover(candidate),
                        confirmation.request,
                    )
                PersistenceTransition(
                    confirmation.next.withActive(operation),
                    PersistenceRequestResult.AwaitingConfirmation(confirmation.request),
                )
            }

            ConfirmationCreation.Exhausted -> {
                val exhausted = creation.next.exhausted()
                PersistenceTransition(exhausted.next, exhausted.result)
            }
        }
    }

    fun adopt(
        coordination: PersistenceCoordination,
        candidate: RuntimeOwners,
    ): PersistenceTransition<PersistenceRequestResult> {
        val recovery = coordination.recoveryState as? RuntimeRecoveryState.Candidate
        return when {
            recovery == null -> {
                PersistenceTransition(coordination.withActive(null), PersistenceRequestResult.Stale)
            }

            coordination.runtimeGeneration == Long.MAX_VALUE -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, exhausted.result)
            }

            else -> {
                install(coordination, candidate, recovery.generation)
            }
        }
    }

    private fun install(
        coordination: PersistenceCoordination,
        candidate: RuntimeOwners,
        generation: RecoveryGeneration,
    ): PersistenceTransition<PersistenceRequestResult> {
        val advanced =
            coordination
                .advanceRuntimeGeneration()
                .withRecovery(RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Present(generation)))
                .finished(PersistenceLastOutcome.Recovered)
        return PersistenceTransition(
            advanced.withAutosave(AutosaveTracking.initial()),
            PersistenceRequestResult.Completed(PersistenceLastOutcome.Recovered),
            RuntimeOwnerEffect.ReplaceOwners(candidate),
        )
    }
}
