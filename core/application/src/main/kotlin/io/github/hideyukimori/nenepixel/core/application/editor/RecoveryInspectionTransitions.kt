package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.ClassifiedImport
import io.github.hideyukimori.nenepixel.core.application.persistence.ClassifiedRecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspectionFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason

internal sealed interface RecoveryInspectionStart {
    data object Started : RecoveryInspectionStart

    data object AlreadyReady : RecoveryInspectionStart

    data object Busy : RecoveryInspectionStart
}

internal object RecoveryInspectionTransitions {
    fun begin(coordination: PersistenceCoordination): PersistenceTransition<RecoveryInspectionStart> =
        when {
            coordination.inspectionInFlight || coordination.activeOperation != null -> {
                PersistenceTransition(coordination, RecoveryInspectionStart.Busy)
            }

            coordination.recoveryState.isReady() -> {
                PersistenceTransition(coordination, RecoveryInspectionStart.AlreadyReady)
            }

            else -> {
                PersistenceTransition(
                    coordination
                        .withAutosave(coordination.autosave.withoutPublishedState())
                        .withInspection(true)
                        .withRecovery(RuntimeRecoveryState.Initializing),
                    RecoveryInspectionStart.Started,
                )
            }
        }

    fun complete(
        coordination: PersistenceCoordination,
        outcome: ClassifiedRecoveryInspection,
    ): PersistenceTransition<RecoveryInitializationResult> =
        if (!coordination.inspectionInFlight) {
            PersistenceTransition(coordination, RecoveryInitializationResult.AlreadyReady)
        } else {
            applyOutcome(coordination.withInspection(false), outcome)
        }

    private fun applyOutcome(
        coordination: PersistenceCoordination,
        outcome: ClassifiedRecoveryInspection,
    ): PersistenceTransition<RecoveryInitializationResult> =
        when (outcome) {
            ClassifiedRecoveryInspection.Missing -> {
                ready(coordination, RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Missing))
            }

            is ClassifiedRecoveryInspection.Retired -> {
                ready(coordination, RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Present(outcome.generation)))
            }

            is ClassifiedRecoveryInspection.Candidate -> {
                when (val source = outcome.source) {
                    is ClassifiedImport.Current -> {
                        ready(coordination, RuntimeRecoveryState.Candidate(outcome.generation, source.document))
                    }

                    is ClassifiedImport.Legacy -> {
                        ready(coordination, RuntimeRecoveryState.LegacyCandidate(outcome.generation, source.candidate))
                    }
                }
            }

            is ClassifiedRecoveryInspection.Failed -> {
                failed(coordination, outcome.failure)
            }
        }

    private fun ready(
        coordination: PersistenceCoordination,
        state: RuntimeRecoveryState,
    ): PersistenceTransition<RecoveryInitializationResult> =
        PersistenceTransition(coordination.withRecovery(state), RecoveryInitializationResult.Ready)

    private fun failed(
        coordination: PersistenceCoordination,
        failure: RecoveryInspectionFailure,
    ): PersistenceTransition<RecoveryInitializationResult> {
        val next =
            coordination
                .withRecovery(RuntimeRecoveryState.Unknown(RecoveryUnavailableReason.Inspection(failure)))
                .copy(lastOutcome = PersistenceLastOutcome.Failed(PersistenceFailure.RecoveryInspection(failure)))
        return PersistenceTransition(next, RecoveryInitializationResult.Failed(failure))
    }
}
