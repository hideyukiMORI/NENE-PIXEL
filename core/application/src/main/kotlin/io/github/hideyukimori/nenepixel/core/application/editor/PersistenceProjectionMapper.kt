package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection

internal object PersistenceProjectionMapper {
    fun project(coordination: PersistenceCoordination): PersistenceOperationProjection =
        PersistenceOperationProjection(
            phase = phase(coordination),
            lastOutcome = coordination.lastOutcome,
            recoveryStatus = coordination.recoveryState.toProjection(),
        )

    fun projectAutosave(coordination: PersistenceCoordination): AutosaveProjection {
        val autosave = coordination.autosave
        val publishingStateToken =
            (coordination.activeOperation as? ActivePersistenceOperation.Autosave)
                ?.capture
                ?.stateToken
        return AutosaveProjection(
            pendingStateToken = autosave.pending?.stateToken,
            publishedStateToken = autosave.publishedStateToken,
            publishingStateToken = publishingStateToken,
            lastOutcome = autosave.lastOutcome,
        )
    }

    private fun phase(coordination: PersistenceCoordination): PersistenceOperationPhase =
        when (val active = coordination.activeOperation) {
            null -> {
                idlePhase(coordination)
            }

            is ActivePersistenceOperation.Save -> {
                active.toProjectionPhase()
            }

            is ActivePersistenceOperation.Export -> {
                if (active.phase == ExportPhase.Cancelling) {
                    PersistenceOperationPhase.Cancelling(active.handle)
                } else {
                    PersistenceOperationPhase.Exporting(active.handle)
                }
            }

            is ActivePersistenceOperation.Switch -> {
                active.toProjectionPhase()
            }

            is ActivePersistenceOperation.Autosave -> {
                idlePhase(coordination)
            }

            is ActivePersistenceOperation.RecoveryDecline -> {
                PersistenceOperationPhase.Discarding(active.handle)
            }
        }

    private fun idlePhase(coordination: PersistenceCoordination): PersistenceOperationPhase =
        if (coordination.inspectionInFlight || coordination.recoveryState is RuntimeRecoveryState.Initializing) {
            PersistenceOperationPhase.Initializing
        } else {
            PersistenceOperationPhase.Idle
        }
}
