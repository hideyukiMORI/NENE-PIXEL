package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome

internal sealed interface LoadTransportCompletion {
    data class Ready(
        val handle: PersistenceOperationHandle,
    ) : LoadTransportCompletion

    data class Confirmation(
        val request: PersistenceConfirmationRequest,
    ) : LoadTransportCompletion

    data class Result(
        val result: PersistenceRequestResult,
    ) : LoadTransportCompletion

    data object Stale : LoadTransportCompletion
}

internal object LoadTransportTransitions {
    fun complete(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        outcome: ProjectLoadOutcome,
        context: SwitchContext,
    ): PersistenceTransition<LoadTransportCompletion> {
        val operation = coordination.activeOperation
        return when {
            operation == null || operation.handle != handle -> {
                PersistenceTransition(coordination, LoadTransportCompletion.Stale)
            }

            operation is ActivePersistenceOperation.Switch.Cancelling -> {
                cancelled(coordination)
            }

            operation !is ActivePersistenceOperation.Switch.Loading -> {
                PersistenceTransition(coordination, LoadTransportCompletion.Stale)
            }

            else -> {
                applyOutcome(coordination, operation, outcome, context)
            }
        }
    }

    private fun applyOutcome(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Loading,
        outcome: ProjectLoadOutcome,
        context: SwitchContext,
    ): PersistenceTransition<LoadTransportCompletion> =
        when (outcome) {
            is ProjectLoadOutcome.Loaded -> {
                prepare(coordination, operation, context.loadedOwners(outcome.document), context)
            }

            ProjectLoadOutcome.Cancelled -> {
                cancelled(coordination)
            }

            is ProjectLoadOutcome.Failed -> {
                val failure = PersistenceFailure.Storage(outcome.failure, PartialOutputCleanup.NOT_NEEDED)
                completed(coordination, PersistenceLastOutcome.Failed(failure))
            }
        }

    private fun prepare(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Loading,
        candidate: RuntimeOwners,
        context: SwitchContext,
    ): PersistenceTransition<LoadTransportCompletion> =
        if (context.source == operation.consentedSource) {
            val ready =
                ActivePersistenceOperation.Switch.Ready(
                    operation.handle,
                    context.source,
                    candidate,
                    SwitchKind.Loaded,
                )
            PersistenceTransition(coordination.withActive(ready), LoadTransportCompletion.Ready(operation.handle))
        } else {
            awaitConfirmation(coordination, operation, candidate, context)
        }

    private fun awaitConfirmation(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Loading,
        candidate: RuntimeOwners,
        context: SwitchContext,
    ): PersistenceTransition<LoadTransportCompletion> {
        val reason = ConfirmationPolicy.sourceChangedReason(coordination)
        return when (val creation = coordination.nextConfirmation(operation.handle, reason)) {
            is ConfirmationCreation.Created -> {
                val confirming =
                    ActivePersistenceOperation.Switch.Confirming(
                        operation.handle,
                        context.source,
                        PendingSwitch.Prepared(candidate, SwitchKind.Loaded),
                        creation.request,
                    )
                PersistenceTransition(
                    creation.next.withActive(confirming),
                    LoadTransportCompletion.Confirmation(creation.request),
                )
            }

            ConfirmationCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, LoadTransportCompletion.Result(exhausted.result))
            }
        }
    }

    private fun cancelled(coordination: PersistenceCoordination): PersistenceTransition<LoadTransportCompletion> =
        completed(coordination, PersistenceLastOutcome.Cancelled)

    private fun completed(
        coordination: PersistenceCoordination,
        outcome: PersistenceLastOutcome,
    ): PersistenceTransition<LoadTransportCompletion> {
        val transition = coordination.completed(outcome)
        return PersistenceTransition(transition.next, LoadTransportCompletion.Result(transition.result))
    }
}
