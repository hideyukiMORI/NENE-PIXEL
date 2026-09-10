package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationReason
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

internal data class PersistenceIdentity(
    val runtimeGeneration: Long,
    val nextOperationId: Long,
    val nextConfirmationId: Long,
)

internal data class RecoveryTracking(
    val state: RuntimeRecoveryState,
    val inspectionInFlight: Boolean,
    val autosave: AutosaveTracking,
)

internal sealed interface OperationHandleCreation {
    data class Created(
        val handle: PersistenceOperationHandle,
        val next: PersistenceCoordination,
    ) : OperationHandleCreation

    data object Exhausted : OperationHandleCreation
}

internal sealed interface ConfirmationCreation {
    data class Created(
        val request: PersistenceConfirmationRequest,
        val next: PersistenceCoordination,
    ) : ConfirmationCreation

    data object Exhausted : ConfirmationCreation
}

internal data class PersistenceCoordination(
    val identity: PersistenceIdentity,
    val activeOperation: ActivePersistenceOperation?,
    val recovery: RecoveryTracking,
    val lastOutcome: PersistenceLastOutcome,
) {
    val runtimeGeneration: Long
        get() = identity.runtimeGeneration

    val recoveryState: RuntimeRecoveryState
        get() = recovery.state

    val inspectionInFlight: Boolean
        get() = recovery.inspectionInFlight

    val autosave: AutosaveTracking
        get() = recovery.autosave

    fun withActive(operation: ActivePersistenceOperation?): PersistenceCoordination = copy(activeOperation = operation)

    fun withRecovery(state: RuntimeRecoveryState): PersistenceCoordination =
        copy(recovery = recovery.copy(state = state))

    fun withInspection(inFlight: Boolean): PersistenceCoordination =
        copy(recovery = recovery.copy(inspectionInFlight = inFlight))

    fun withAutosave(tracking: AutosaveTracking): PersistenceCoordination =
        copy(recovery = recovery.copy(autosave = tracking))

    fun finished(outcome: PersistenceLastOutcome): PersistenceCoordination =
        copy(activeOperation = null, lastOutcome = outcome)

    fun identityExhausted(): PersistenceCoordination = copy(lastOutcome = IDENTITY_EXHAUSTED)

    fun advanceRuntimeGeneration(): PersistenceCoordination =
        copy(identity = identity.copy(runtimeGeneration = identity.runtimeGeneration + 1L))

    fun nextOperationHandle(): OperationHandleCreation =
        if (identity.nextOperationId == Long.MAX_VALUE) {
            OperationHandleCreation.Exhausted
        } else {
            OperationHandleCreation.Created(
                PersistenceOperationHandle(identity.nextOperationId),
                copy(identity = identity.copy(nextOperationId = identity.nextOperationId + 1L)),
            )
        }

    fun nextConfirmation(
        handle: PersistenceOperationHandle,
        reason: PersistenceConfirmationReason,
    ): ConfirmationCreation =
        if (identity.nextConfirmationId == Long.MAX_VALUE) {
            ConfirmationCreation.Exhausted
        } else {
            ConfirmationCreation.Created(
                PersistenceConfirmationRequest(handle, reason, identity.nextConfirmationId),
                copy(identity = identity.copy(nextConfirmationId = identity.nextConfirmationId + 1L)),
            )
        }

    companion object {
        fun initial(): PersistenceCoordination =
            PersistenceCoordination(
                PersistenceIdentity(INITIAL_RUNTIME_GENERATION, INITIAL_OPERATION_ID, INITIAL_CONFIRMATION_ID),
                null,
                RecoveryTracking(
                    RuntimeRecoveryState.Initializing,
                    false,
                    AutosaveTracking.initial(INITIAL_RUNTIME_GENERATION),
                ),
                PersistenceLastOutcome.None,
            )

        private val IDENTITY_EXHAUSTED: PersistenceLastOutcome =
            PersistenceLastOutcome.Failed(PersistenceFailure.IdentityExhausted)

        private const val INITIAL_RUNTIME_GENERATION: Long = 1L
        private const val INITIAL_OPERATION_ID: Long = 1L
        private const val INITIAL_CONFIRMATION_ID: Long = 1L
    }
}
