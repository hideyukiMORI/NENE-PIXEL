package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult

internal sealed interface SwitchContinuation {
    data class Load(
        val handle: PersistenceOperationHandle,
    ) : SwitchContinuation

    data class Ready(
        val handle: PersistenceOperationHandle,
    ) : SwitchContinuation

    data class Confirmation(
        val request: PersistenceConfirmationRequest,
    ) : SwitchContinuation

    data class Result(
        val result: PersistenceRequestResult,
    ) : SwitchContinuation

    data object Stale : SwitchContinuation

    data object TooLate : SwitchContinuation
}

internal object SwitchConfirmationTransitions {
    fun confirm(
        coordination: PersistenceCoordination,
        request: PersistenceConfirmationRequest,
        context: SwitchContext,
    ): PersistenceTransition<SwitchContinuation> {
        val operation = coordination.activeOperation
        return when {
            operation == null || operation.handle != request.operation -> {
                PersistenceTransition(coordination, SwitchContinuation.Stale)
            }

            operation is ActivePersistenceOperation.Switch.Switching -> {
                PersistenceTransition(coordination, SwitchContinuation.TooLate)
            }

            operation !is ActivePersistenceOperation.Switch.Confirming -> {
                PersistenceTransition(coordination, SwitchContinuation.Stale)
            }

            operation.request !== request -> {
                PersistenceTransition(coordination, SwitchContinuation.Stale)
            }

            else -> {
                accept(coordination, operation, context)
            }
        }
    }

    private fun accept(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Confirming,
        context: SwitchContext,
    ): PersistenceTransition<SwitchContinuation> =
        if (context.source == operation.issuedSource) {
            applyPending(coordination, operation, context)
        } else {
            refresh(coordination, operation, context)
        }

    private fun refresh(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Confirming,
        context: SwitchContext,
    ): PersistenceTransition<SwitchContinuation> {
        val reason = ConfirmationPolicy.refreshedReason(coordination, operation.pending, context.dirty)
        return when (val creation = coordination.nextConfirmation(operation.handle, reason)) {
            is ConfirmationCreation.Created -> {
                val next = operation.copy(issuedSource = context.source, request = creation.request)
                PersistenceTransition(
                    creation.next.withActive(next),
                    SwitchContinuation.Confirmation(creation.request),
                )
            }

            ConfirmationCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, SwitchContinuation.Result(exhausted.result))
            }
        }
    }

    private fun applyPending(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.Confirming,
        context: SwitchContext,
    ): PersistenceTransition<SwitchContinuation> =
        when (val pending = operation.pending) {
            PendingSwitch.Load -> {
                val loading = ActivePersistenceOperation.Switch.Loading(operation.handle, context.source)
                PersistenceTransition(coordination.withActive(loading), SwitchContinuation.Load(operation.handle))
            }

            is PendingSwitch.New -> {
                ready(coordination, operation.handle, context.newDocumentOwners(pending.request), context.source)
            }

            is PendingSwitch.Prepared -> {
                prepared(coordination, operation.handle, pending, context.source)
            }
        }

    private fun ready(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        candidate: RuntimeOwners,
        source: RuntimeSourceToken,
    ): PersistenceTransition<SwitchContinuation> {
        val operation =
            ActivePersistenceOperation.Switch.Ready(handle, source, candidate, SwitchKind.NewDocument)
        return PersistenceTransition(coordination.withActive(operation), SwitchContinuation.Ready(handle))
    }

    private fun prepared(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        pending: PendingSwitch.Prepared,
        source: RuntimeSourceToken,
    ): PersistenceTransition<SwitchContinuation> {
        val operation =
            ActivePersistenceOperation.Switch.Ready(handle, source, pending.candidate, pending.kind)
        return PersistenceTransition(coordination.withActive(operation), SwitchContinuation.Ready(handle))
    }
}
