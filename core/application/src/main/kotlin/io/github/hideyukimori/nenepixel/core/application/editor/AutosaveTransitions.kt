package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal sealed interface AutosaveStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val capture: AutosaveCapture,
        val expected: ExpectedRecoveryLineage,
    ) : AutosaveStart

    data object NoCapture : AutosaveStart

    data object Deferred : AutosaveStart

    data object OfferPending : AutosaveStart

    data object Unavailable : AutosaveStart

    data object IdentityExhausted : AutosaveStart
}

internal object AutosaveTransitions {
    fun recordCapture(
        coordination: PersistenceCoordination,
        document: DocumentState,
        historyPosition: HistoryPosition,
    ): PersistenceCoordination {
        val autosave = coordination.autosave
        val stateToken = AutosaveStateToken(coordination.runtimeGeneration, historyPosition)
        val canReusePublishedState = coordination.activeOperation == null && !coordination.inspectionInFlight
        return if (canReusePublishedState && autosave.publishedStateToken == stateToken) {
            coordination.withAutosave(autosave.abandoned())
        } else {
            coordination.withAutosave(
                autosave.recorded(AutosaveCapture(document, stateToken)),
            )
        }
    }

    fun begin(coordination: PersistenceCoordination): PersistenceTransition<AutosaveStart> =
        if (coordination.activeOperation != null || coordination.inspectionInFlight) {
            PersistenceTransition(
                coordination.withAutosave(coordination.autosave.withOutcome(AutosaveLastOutcome.Deferred)),
                AutosaveStart.Deferred,
            )
        } else {
            start(coordination)
        }

    private fun start(coordination: PersistenceCoordination): PersistenceTransition<AutosaveStart> {
        val pending = coordination.autosave.pending
        return if (pending == null || !pending.stateToken.belongsTo(coordination.runtimeGeneration)) {
            PersistenceTransition(
                coordination.withAutosave(coordination.autosave.abandoned()),
                AutosaveStart.NoCapture,
            )
        } else if (coordination.recoveryState is RuntimeRecoveryState.Candidate) {
            PersistenceTransition(
                coordination.withAutosave(coordination.autosave.withOutcome(AutosaveLastOutcome.OfferPending)),
                AutosaveStart.OfferPending,
            )
        } else {
            when (val lineage = coordination.recoveryState.expectedLineage()) {
                is ExpectedLineageResult.Available -> created(coordination, pending, lineage.expected)
                ExpectedLineageResult.Unavailable -> PersistenceTransition(coordination, AutosaveStart.Unavailable)
            }
        }
    }

    private fun created(
        coordination: PersistenceCoordination,
        pending: AutosaveCapture,
        expected: ExpectedRecoveryLineage,
    ): PersistenceTransition<AutosaveStart> =
        when (val creation = coordination.nextOperationHandle()) {
            is OperationHandleCreation.Created -> {
                PersistenceTransition(
                    creation.next.withActive(
                        ActivePersistenceOperation.Autosave(creation.handle, pending, expected),
                    ),
                    AutosaveStart.Started(creation.handle, pending, expected),
                )
            }

            OperationHandleCreation.Exhausted -> {
                PersistenceTransition(coordination.identityExhausted(), AutosaveStart.IdentityExhausted)
            }
        }

    fun complete(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        outcome: RecoveryPublicationOutcome,
    ): PersistenceTransition<AutosaveRequestResult> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Autosave
        return if (operation == null || operation.handle != handle) {
            PersistenceTransition(coordination, AutosaveRequestResult.Stale)
        } else {
            finish(coordination, operation.capture, outcome)
        }
    }

    private fun finish(
        coordination: PersistenceCoordination,
        capture: AutosaveCapture,
        outcome: RecoveryPublicationOutcome,
    ): PersistenceTransition<AutosaveRequestResult> =
        when (outcome) {
            is RecoveryPublicationOutcome.Published -> {
                published(coordination, capture, outcome.generation)
            }

            RecoveryPublicationOutcome.Stale -> {
                released(
                    coordination.withRecovery(RuntimeRecoveryState.Unknown(RecoveryUnavailableReason.Stale)),
                    AutosaveLastOutcome.Stale,
                    AutosaveRequestResult.Stale,
                )
            }

            RecoveryPublicationOutcome.GenerationExhausted -> {
                released(
                    coordination,
                    AutosaveLastOutcome.GenerationExhausted,
                    AutosaveRequestResult.GenerationExhausted,
                )
            }

            is RecoveryPublicationOutcome.Failed -> {
                released(
                    coordination,
                    AutosaveLastOutcome.Failed(outcome.failure, outcome.rollback),
                    AutosaveRequestResult.Failed(outcome.failure, outcome.rollback),
                )
            }

            is RecoveryPublicationOutcome.Uncertain -> {
                val reason = RecoveryUnavailableReason.RetirementUncertain(outcome.failure, outcome.rollback)
                released(
                    coordination.withRecovery(RuntimeRecoveryState.Unknown(reason)),
                    AutosaveLastOutcome.Uncertain(outcome.failure, outcome.rollback),
                    AutosaveRequestResult.Uncertain(outcome.failure, outcome.rollback),
                )
            }
        }

    private fun published(
        coordination: PersistenceCoordination,
        capture: AutosaveCapture,
        generation: RecoveryGeneration,
    ): PersistenceTransition<AutosaveRequestResult> =
        PersistenceTransition(
            coordination
                .withRecovery(RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Present(generation)))
                .withAutosave(
                    coordination.autosave.published(capture, AutosaveLastOutcome.Published(generation)),
                ).withActive(null),
            AutosaveRequestResult.Published(generation),
        )

    private fun released(
        coordination: PersistenceCoordination,
        outcome: AutosaveLastOutcome,
        result: AutosaveRequestResult,
    ): PersistenceTransition<AutosaveRequestResult> =
        PersistenceTransition(
            coordination.withAutosave(coordination.autosave.withOutcome(outcome)).withActive(null),
            result,
        )

    fun release(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
    ): PersistenceTransition<Unit> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Autosave
        return if (operation == null || operation.handle != handle) {
            PersistenceTransition(coordination, Unit)
        } else {
            PersistenceTransition(coordination.withActive(null), Unit)
        }
    }
}
