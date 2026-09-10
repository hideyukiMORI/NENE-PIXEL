package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The recovery offer and the autosave status line are both derived from read-only projections, so
 * they are decided on the host against a real workflow with fake ports.
 */
internal class RecoveryOfferStatusTest {
    @Test
    fun unadoptedCandidateReplacesTheStatusTextWithAnEnabledOffer() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, StartupCandidateRecoveryRecordPort(fixture.initialDocument))

        runBlocking { workflow.initializeRecovery() }

        val operation = workflow.operation.value
        assertTrue(operation.offersRecovery())
        assertTrue(operation.recoveryOfferEnabled())
    }

    @Test
    fun clearRecoveryKeepsTheOrdinaryStatusTextAndNoOffer() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, MissingRecoveryRecordPort())

        runBlocking { workflow.initializeRecovery() }

        val operation = workflow.operation.value
        assertFalse(operation.offersRecovery())
        assertEquals("Project storage ready", operation.statusText(workflow.autosave.value))
    }

    @Test
    fun failedAutosavePublicationIsReportedOnTheOneStatusLine() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, FailingRecoveryRecordPort())
        runBlocking { workflow.initializeRecovery() }
        commitStroke(fixture)

        runBlocking { workflow.publishLatestCapture() }

        val operation = workflow.operation.value
        assertFalse(operation.offersRecovery())
        assertEquals("Autosave failed", operation.statusText(workflow.autosave.value))
    }

    private fun workflow(
        fixture: EditorFixture,
        recoveryRecord: RecoveryRecordPort,
    ): EditorPersistenceWorkflow =
        EditorPersistenceWorkflow.create(fixture.runtime, CancellingProjectStoragePort, recoveryRecord)

    private fun commitStroke(fixture: EditorFixture) {
        val begun =
            fixture.reducer
                .reduce(
                    fixture.initialWorkspace,
                    WorkspaceAction.BeginGesturePreview(
                        fixture.initialDocument.size,
                        PresentationTestValues.position(0, 0),
                    ),
                ).nextState
        val extended =
            fixture.reducer
                .reduce(begun, WorkspaceAction.ExtendGesturePreview(PresentationTestValues.position(1, 0)))
                .nextState
        val prepared = fixture.reducer.reduce(extended, WorkspaceAction.PrepareGestureCommit)
        val commit = assertInstanceOf(WorkspaceReductionResult.CommitPrepared::class.java, prepared)
        val target = fixture.runtime.state.documentState
        val result = fixture.runtime.execute(ApplyStrokeCommand.create(target.id, target.revision, commit.stroke))
        assertInstanceOf(CommandResult.Applied::class.java, result)
    }
}

private data object CancellingProjectStoragePort : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled
}

private class StartupCandidateRecoveryRecordPort(
    private val document: DocumentState,
) : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Candidate(generation(1L), document)

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Retired(generation(2L))

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome = RecoveryPublicationOutcome.Published(generation(2L))
}

private class MissingRecoveryRecordPort : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Retired(generation(1L))

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome = RecoveryPublicationOutcome.Published(generation(1L))
}

private class FailingRecoveryRecordPort : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Retired(generation(1L))

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome =
        RecoveryPublicationOutcome.Failed(RecoveryRetirementFailure.WRITE, RecoveryRollbackOutcome.COMPLETED)
}

private fun generation(value: Long): RecoveryGeneration =
    when (val result = RecoveryGeneration.create(value)) {
        is RecoveryGenerationResult.Created -> result.generation
        RecoveryGenerationResult.Rejected -> error("Invalid test recovery generation: $value")
    }
