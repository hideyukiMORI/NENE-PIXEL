package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationReason
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

internal sealed interface SwitchStart {
    data class Load(
        val handle: PersistenceOperationHandle,
    ) : SwitchStart

    data class Ready(
        val handle: PersistenceOperationHandle,
    ) : SwitchStart

    data class Confirmation(
        val request: PersistenceConfirmationRequest,
    ) : SwitchStart

    data class Rejected(
        val rejection: NewDocumentRejection,
    ) : SwitchStart

    data object Busy : SwitchStart

    data object RecoveryUnavailable : SwitchStart

    data object IdentityExhausted : SwitchStart
}

internal data class SwitchConfirmationStart(
    val handle: PersistenceOperationHandle,
    val pending: PendingSwitch,
    val reason: PersistenceConfirmationReason,
    val source: RuntimeSourceToken,
)

internal object SwitchStartTransitions {
    fun beginLoad(
        coordination: PersistenceCoordination,
        context: SwitchContext,
    ): PersistenceTransition<SwitchStart> = begin(coordination, SwitchIntent.Load, context)

    fun beginNewDocument(
        coordination: PersistenceCoordination,
        request: NewDocumentRequestResult,
        context: SwitchContext,
    ): PersistenceTransition<SwitchStart> =
        when (request) {
            is NewDocumentRequestResult.Created -> {
                begin(coordination, SwitchIntent.New(request.request), context)
            }

            is NewDocumentRequestResult.Rejected -> {
                PersistenceTransition(coordination, SwitchStart.Rejected(request.rejection))
            }
        }

    private fun begin(
        coordination: PersistenceCoordination,
        intent: SwitchIntent,
        context: SwitchContext,
    ): PersistenceTransition<SwitchStart> =
        when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, SwitchStart.Busy)
            }

            coordination.recoveryState.blocksSwitch() -> {
                PersistenceTransition(coordination, SwitchStart.RecoveryUnavailable)
            }

            else -> {
                start(coordination, intent, context)
            }
        }

    private fun start(
        coordination: PersistenceCoordination,
        intent: SwitchIntent,
        context: SwitchContext,
    ): PersistenceTransition<SwitchStart> =
        when (val creation = coordination.nextOperationHandle()) {
            is OperationHandleCreation.Created -> {
                route(creation.next, creation.handle, intent, context)
            }

            OperationHandleCreation.Exhausted -> {
                PersistenceTransition(coordination.identityExhausted(), SwitchStart.IdentityExhausted)
            }
        }

    private fun route(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        intent: SwitchIntent,
        context: SwitchContext,
    ): PersistenceTransition<SwitchStart> =
        when (val need = ConfirmationPolicy.initialNeed(coordination, context.dirty)) {
            ConfirmationNeed.NotRequired -> {
                confirmed(coordination, handle, intent, context)
            }

            is ConfirmationNeed.Required -> {
                awaitConfirmation(
                    coordination,
                    SwitchConfirmationStart(handle, intent.toPendingSwitch(), need.reason, context.source),
                )
            }
        }

    private fun confirmed(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        intent: SwitchIntent,
        context: SwitchContext,
    ): PersistenceTransition<SwitchStart> =
        when (intent) {
            SwitchIntent.Load -> {
                val operation = ActivePersistenceOperation.Switch.Loading(handle, context.source)
                PersistenceTransition(coordination.withActive(operation), SwitchStart.Load(handle))
            }

            is SwitchIntent.New -> {
                val operation =
                    ActivePersistenceOperation.Switch.Ready(
                        handle,
                        context.source,
                        context.newDocumentOwners(intent.request),
                        SwitchKind.NewDocument,
                    )
                PersistenceTransition(coordination.withActive(operation), SwitchStart.Ready(handle))
            }
        }

    private fun awaitConfirmation(
        coordination: PersistenceCoordination,
        start: SwitchConfirmationStart,
    ): PersistenceTransition<SwitchStart> =
        when (val creation = coordination.nextConfirmation(start.handle, start.reason)) {
            is ConfirmationCreation.Created -> {
                val operation =
                    ActivePersistenceOperation.Switch.Confirming(
                        start.handle,
                        start.source,
                        start.pending,
                        creation.request,
                    )
                PersistenceTransition(
                    creation.next.withActive(operation),
                    SwitchStart.Confirmation(creation.request),
                )
            }

            ConfirmationCreation.Exhausted -> {
                PersistenceTransition(coordination.identityExhausted(), SwitchStart.IdentityExhausted)
            }
        }
}
