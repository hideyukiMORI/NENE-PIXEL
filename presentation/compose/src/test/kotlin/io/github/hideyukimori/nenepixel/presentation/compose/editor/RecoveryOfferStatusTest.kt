package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PalettePersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PngPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectPersistencePorts
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
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.geometry.toDocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.R
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
    private val dispatchers = TestCoroutineDispatchers()

    @Test
    fun unadoptedCandidateReplacesTheStatusTextWithAnEnabledOffer() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, StartupCandidateRecoveryRecordPort(fixture.initialDocument))

        runBlocking { workflow.initializeRecovery() }

        val operation = workflow.operation.value
        assertTrue(operation.offersRecovery())
        assertTrue(operation.recoveryOfferEnabled(EditorOperationAvailability.Available))
        assertFalse(operation.recoveryOfferEnabled(EditorOperationAvailability.Busy))
        assertEquals(
            R.string.preparing_operation,
            operation.statusResource(workflow.autosave.value, EditorOperationAvailability.Busy),
        )
    }

    @Test
    fun clearRecoveryKeepsTheOrdinaryStatusTextAndNoOffer() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, MissingRecoveryRecordPort())

        runBlocking { workflow.initializeRecovery() }

        val operation = workflow.operation.value
        assertFalse(operation.offersRecovery())
        assertEquals(
            R.string.storage_ready,
            operation.statusResource(workflow.autosave.value, EditorOperationAvailability.Available),
        )
        assertEquals(
            R.string.preparing_operation,
            operation.statusResource(workflow.autosave.value, EditorOperationAvailability.Busy),
        )
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
        assertEquals(
            R.string.autosave_failed,
            operation.statusResource(workflow.autosave.value, EditorOperationAvailability.Available),
        )
    }

    @Test
    fun confirmationKeepsItsSpecificStatusWhileAContinuationOwnsAdmission() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, MissingRecoveryRecordPort())
        runBlocking { workflow.initializeRecovery() }
        commitStroke(fixture)
        runBlocking { workflow.createNewDocument(NewDocumentRequest.create("2", "2")) }

        val operation = workflow.operation.value
        assertInstanceOf(PersistenceOperationPhase.NeedsConfirmation::class.java, operation.phase)
        assertEquals(
            R.string.waiting_confirmation,
            operation.statusResource(workflow.autosave.value, EditorOperationAvailability.Busy),
        )
        assertFalse(operation.recoveryOfferEnabled(EditorOperationAvailability.Available))
    }

    @Test
    fun retainedJobBlocksJsonExchangeWithoutBlockingIdlePaletteDraftEditing() {
        val fixture = PresentationTestValues.fixture()
        val workflow = workflow(fixture, MissingRecoveryRecordPort())
        val callbacks = persistenceCallbacks(fixture.initialDocument.definition)
        runBlocking { workflow.initializeRecovery() }
        val busy = PaletteJsonActions(callbacks, workflow.operation.value, EditorOperationAvailability.Busy)
        assertTrue(busy.idle)
        assertFalse(busy.canExchange)
        val available = PaletteJsonActions(callbacks, workflow.operation.value, EditorOperationAvailability.Available)
        assertTrue(available.idle)
        assertTrue(available.canExchange)
        commitStroke(fixture)
        runBlocking { workflow.createNewDocument(NewDocumentRequest.create("2", "2")) }
        val confirming = PaletteJsonActions(callbacks, workflow.operation.value, EditorOperationAvailability.Available)
        assertFalse(confirming.idle)
        assertFalse(confirming.canExchange)
    }

    private fun persistenceCallbacks(definition: PaletteDefinition): EditorPersistenceCallbacks =
        EditorPersistenceCallbacks.create(
            ProjectFileCallbacks(
                FileExchangeCallbacks(PngExchangeCallbacks({}, {}, {}), PaletteExchangeCallbacks({}, {}), {}),
                {},
                {},
                {},
            ),
            PersistenceDecisionCallbacks({}, {}, {}, {}),
            LegacyConversionCallbacks({}, { _, _ -> }, {}, {}),
            LegacyPalettePresets(definition, definition, definition),
        )

    private fun workflow(
        fixture: EditorFixture,
        recoveryRecord: RecoveryRecordPort,
    ): EditorPersistenceWorkflow =
        EditorPersistenceWorkflow.create(
            fixture.runtime,
            PersistencePorts(
                ProjectPersistencePorts(
                    CancellingProjectStoragePort,
                    recoveryRecord,
                ),
                PngPersistencePorts(
                    io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort {
                        io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome.Cancelled
                    },
                    io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort {
                        io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome.Cancelled
                    },
                ),
                PalettePersistencePorts(
                    io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort {
                        io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome.Cancelled
                    },
                    io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort {
                        io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome.Cancelled
                    },
                ),
                UnderlayPersistencePorts(
                    io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort {
                        io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome.Cancelled
                    },
                    EmptyUnderlayMemoryPort,
                ),
            ),
            dispatchers.inline,
        )

    private fun commitStroke(fixture: EditorFixture) {
        val begun =
            fixture.reducer
                .reduce(
                    fixture.initialWorkspace,
                    WorkspaceAction.BeginGesturePreview(
                        fixture.initialDocument.size,
                        PresentationTestValues.position(0, 0),
                    ),
                    fixture.runtime.captureSource(),
                ).nextState
        val extended =
            fixture.reducer
                .reduce(
                    begun,
                    WorkspaceAction.ExtendGesturePreview(PresentationTestValues.position(1, 0).toDocumentPoint()),
                    fixture.runtime.captureSource(),
                ).nextState
        val prepared =
            fixture.reducer.reduce(
                extended,
                WorkspaceAction.PrepareGestureCommit,
                fixture.runtime.captureSource(),
            )
        val commit = assertInstanceOf(WorkspaceReductionResult.CommitPrepared::class.java, prepared)
        val result =
            fixture.runtime.execute(
                ApplyStrokeCommand.create(fixture.runtime.captureSource(), commit.layerId, commit.stroke),
            )
        assertInstanceOf(CommandResult.Applied::class.java, result)
    }
}

private data object CancellingProjectStoragePort : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled
}

private class StartupCandidateRecoveryRecordPort(
    private val document: DocumentState,
) : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection =
        RecoveryInspection.Candidate(generation(1L), DocumentImportSource.Current(document))

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
