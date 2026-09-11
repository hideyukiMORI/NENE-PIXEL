package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryCleanupOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal sealed interface SaveStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val document: DocumentState,
    ) : SaveStart

    data object Busy : SaveStart

    data object RecoveryUnavailable : SaveStart

    data object IdentityExhausted : SaveStart
}

internal sealed interface SaveTransportCompletion {
    data class Cleanup(
        val handle: PersistenceOperationHandle,
        val expected: ExpectedRecoveryLineage,
    ) : SaveTransportCompletion

    data class Result(
        val result: PersistenceRequestResult,
    ) : SaveTransportCompletion

    data object Stale : SaveTransportCompletion
}

internal object SaveTransitions {
    fun begin(
        coordination: PersistenceCoordination,
        document: DocumentState,
        historyPosition: HistoryPosition,
    ): PersistenceTransition<SaveStart> =
        when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, SaveStart.Busy)
            }

            coordination.recoveryState is RuntimeRecoveryState.Initializing -> {
                PersistenceTransition(coordination, SaveStart.RecoveryUnavailable)
            }

            else -> {
                start(coordination, document, historyPosition)
            }
        }

    private fun start(
        coordination: PersistenceCoordination,
        document: DocumentState,
        historyPosition: HistoryPosition,
    ): PersistenceTransition<SaveStart> =
        when (val creation = coordination.nextOperationHandle()) {
            is OperationHandleCreation.Created -> {
                started(creation, document, historyPosition)
            }

            OperationHandleCreation.Exhausted -> {
                PersistenceTransition(coordination.identityExhausted(), SaveStart.IdentityExhausted)
            }
        }

    private fun started(
        creation: OperationHandleCreation.Created,
        document: DocumentState,
        historyPosition: HistoryPosition,
    ): PersistenceTransition<SaveStart> {
        val next = creation.next
        val capture =
            SaveCapture(
                document = document,
                historyPosition = historyPosition,
                runtimeGeneration = next.runtimeGeneration,
                recovery = next.recoveryState.toSaveCapture(),
            )
        val operation = ActivePersistenceOperation.Save(creation.handle, capture, SavePhase.Transport)
        return PersistenceTransition(next.withActive(operation), SaveStart.Started(creation.handle, document))
    }

    fun completeTransport(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        outcome: ProjectSaveOutcome,
        currentDocumentId: DocumentId,
    ): PersistenceTransition<SaveTransportCompletion> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Save
        return when {
            operation == null || operation.handle != handle -> {
                PersistenceTransition(coordination, SaveTransportCompletion.Stale)
            }

            operation.phase == SavePhase.Cancelling -> {
                cancelledResult(coordination)
            }

            operation.phase != SavePhase.Transport -> {
                PersistenceTransition(coordination, SaveTransportCompletion.Stale)
            }

            else -> {
                applyTransport(coordination, operation, outcome, currentDocumentId)
            }
        }
    }

    private fun applyTransport(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Save,
        outcome: ProjectSaveOutcome,
        currentDocumentId: DocumentId,
    ): PersistenceTransition<SaveTransportCompletion> =
        when (outcome) {
            ProjectSaveOutcome.Saved -> {
                verified(coordination, operation, currentDocumentId)
            }

            ProjectSaveOutcome.Cancelled -> {
                cancelledResult(coordination)
            }

            is ProjectSaveOutcome.Failed -> {
                val failure = PersistenceFailure.Storage(outcome.failure, outcome.cleanup)
                completedResult(coordination, PersistenceLastOutcome.Failed(failure))
            }
        }

    private fun verified(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Save,
        currentDocumentId: DocumentId,
    ): PersistenceTransition<SaveTransportCompletion> {
        val capture = operation.capture
        val matches =
            capture.runtimeGeneration == coordination.runtimeGeneration && capture.document.id == currentDocumentId
        return if (matches) {
            checkpointed(coordination, operation)
        } else {
            PersistenceTransition(coordination.withActive(null), SaveTransportCompletion.Stale)
        }
    }

    private fun checkpointed(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Save,
    ): PersistenceTransition<SaveTransportCompletion> {
        val capture = operation.capture
        val checkpointed = coordination.withAutosave(coordination.autosave.droppedAt(capture.stateToken))
        val effect =
            RuntimeOwnerEffect.InstallCleanCheckpoint(
                DocumentCleanCheckpoint.create(capture.document.id, capture.historyPosition),
            )
        return when (val recovery = capture.recovery) {
            is SaveRecoveryCapture.Clear -> {
                PersistenceTransition(
                    checkpointed.withActive(operation.copy(phase = SavePhase.Cleanup)),
                    SaveTransportCompletion.Cleanup(operation.handle, recovery.expected),
                    effect,
                )
            }

            SaveRecoveryCapture.UnadoptedCandidate -> {
                saved(checkpointed, RecoveryCleanupOutcome.PreservedUnadoptedCandidate, effect)
            }

            SaveRecoveryCapture.Unavailable -> {
                saved(checkpointed, RecoveryCleanupOutcome.RecoveryUnavailable, effect)
            }
        }
    }

    private fun saved(
        coordination: PersistenceCoordination,
        cleanup: RecoveryCleanupOutcome,
        effect: RuntimeOwnerEffect,
    ): PersistenceTransition<SaveTransportCompletion> {
        val outcome = PersistenceLastOutcome.Saved(cleanup)
        return PersistenceTransition(
            coordination.finished(outcome),
            SaveTransportCompletion.Result(PersistenceRequestResult.Completed(outcome)),
            effect,
        )
    }

    private fun cancelledResult(
        coordination: PersistenceCoordination,
    ): PersistenceTransition<SaveTransportCompletion> = completedResult(coordination, PersistenceLastOutcome.Cancelled)

    private fun completedResult(
        coordination: PersistenceCoordination,
        outcome: PersistenceLastOutcome,
    ): PersistenceTransition<SaveTransportCompletion> =
        PersistenceTransition(
            coordination.finished(outcome),
            SaveTransportCompletion.Result(PersistenceRequestResult.Completed(outcome)),
        )
}
