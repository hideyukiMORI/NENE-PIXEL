package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection

internal object PersistenceProjectionMapper {
    fun project(coordination: PersistenceCoordination): PersistenceOperationProjection =
        PersistenceOperationProjection(
            phase = phase(coordination),
            lastOutcome = coordination.lastOutcome,
            recoveryStatus = coordination.recoveryState.toProjection(),
        )

    private fun phase(coordination: PersistenceCoordination): PersistenceOperationPhase =
        when (val active = coordination.activeOperation) {
            null -> idlePhase(coordination)
            is ActivePersistenceOperation.Save -> active.toProjectionPhase()
            is ActivePersistenceOperation.Switch -> active.toProjectionPhase()
        }

    private fun idlePhase(coordination: PersistenceCoordination): PersistenceOperationPhase =
        if (coordination.inspectionInFlight || coordination.recoveryState is RuntimeRecoveryState.Initializing) {
            PersistenceOperationPhase.Initializing
        } else {
            PersistenceOperationPhase.Idle
        }
}
