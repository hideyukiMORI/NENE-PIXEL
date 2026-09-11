package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntimeState
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class Fixture(
    inspection: RecoveryInspection = RecoveryInspection.Missing,
) {
    val ids = SequentialDocumentIdSource()
    val runtime = EditorRuntime.create(canvas(4, 4), palette(red, green), ids)
    val storage = FakeProjectStoragePort()
    val recovery = FakeRecoveryRecordPort(inspection)
    val workflow = EditorPersistenceWorkflow.create(runtime, storage, recovery)

    suspend fun initialize() {
        assertEquals(RecoveryInitializationResult.Ready, workflow.initializeRecovery())
    }
}

internal class FakeProjectStoragePort : ProjectStoragePort {
    var saveHandler: suspend (DocumentState) -> ProjectSaveOutcome = { ProjectSaveOutcome.Cancelled }
    var loadHandler: suspend () -> ProjectLoadOutcome = { ProjectLoadOutcome.Cancelled }
    val savedDocuments = mutableListOf<DocumentState>()
    var loadCalls: Int = 0
        private set

    override suspend fun save(document: DocumentState): ProjectSaveOutcome {
        savedDocuments += document
        return saveHandler(document)
    }

    override suspend fun load(): ProjectLoadOutcome {
        loadCalls += 1
        return loadHandler()
    }
}

internal data class RecoveryPublication(
    val expected: ExpectedRecoveryLineage,
    val document: DocumentState,
)

internal class FakeRecoveryRecordPort(
    var inspection: RecoveryInspection,
) : RecoveryRecordPort {
    var inspectCalls: Int = 0
        private set
    val retireCalls = mutableListOf<ExpectedRecoveryLineage>()
    val publishCalls = mutableListOf<RecoveryPublication>()
    var retireHandler: suspend (ExpectedRecoveryLineage) -> RecoveryRetirementOutcome = {
        RecoveryRetirementOutcome.Retired(generation(1))
    }
    var publishHandler: suspend (RecoveryPublication) -> RecoveryPublicationOutcome = { publication ->
        RecoveryPublicationOutcome.Published(nextGeneration(publication.expected))
    }

    override suspend fun inspect(): RecoveryInspection {
        inspectCalls += 1
        return inspection
    }

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome {
        retireCalls += expected
        return retireHandler(expected)
    }

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome {
        val publication = RecoveryPublication(expected, document)
        publishCalls += publication
        return publishHandler(publication)
    }
}

internal fun nextGeneration(expected: ExpectedRecoveryLineage): RecoveryGeneration =
    when (expected) {
        ExpectedRecoveryLineage.Missing -> generation(1)
        is ExpectedRecoveryLineage.Present -> generation(expected.generation.value + 1L)
    }

internal class SequentialDocumentIdSource : DocumentIdSource {
    var callCount: Int = 0
        private set

    override fun nextDocumentId(): DocumentId {
        val character = ('1'.code + callCount).toChar()
        callCount += 1
        return documentId(character)
    }
}

internal suspend fun initializedFixture(): Fixture = Fixture().also { it.initialize() }

internal fun newRequest(
    width: Int,
    height: Int,
) = NewDocumentRequest.create(width.toString(), height.toString())

internal fun apply(
    runtime: EditorRuntime,
    position: PixelPosition,
    color: PixelColor,
) {
    assertInstanceOf(CommandResult.Applied::class.java, runtime.execute(commandFor(runtime, position, color)))
}

internal fun commandFor(
    runtime: EditorRuntime,
    position: PixelPosition,
    color: PixelColor,
): ApplyStrokeCommand {
    val current = runtime.state.documentState
    return ApplyStrokeCommand.create(
        current.id,
        current.revision,
        stroke(current.size, listOf(position), color),
    )
}

internal fun undo(runtime: EditorRuntime) {
    val current = runtime.state.documentState
    assertInstanceOf(
        CommandResult.Applied::class.java,
        runtime.execute(UndoCommand.create(current.id, current.revision)),
    )
}

internal fun assertFailedInspection(result: RecoveryInitializationResult): RecoveryInspectionFailure =
    assertInstanceOf(RecoveryInitializationResult.Failed::class.java, result).failure

internal fun assertSaved(result: PersistenceRequestResult): PersistenceLastOutcome.Saved =
    assertInstanceOf(
        PersistenceLastOutcome.Saved::class.java,
        assertInstanceOf(PersistenceRequestResult.Completed::class.java, result).outcome,
    )

internal fun assertRetiredCleanup(cleanup: RecoveryCleanupOutcome): RecoveryGeneration =
    assertInstanceOf(RecoveryCleanupOutcome.Retired::class.java, cleanup).generation

internal fun assertFailed(result: PersistenceRequestResult): PersistenceFailure =
    assertInstanceOf(
        PersistenceLastOutcome.Failed::class.java,
        assertInstanceOf(PersistenceRequestResult.Completed::class.java, result).outcome,
    ).failure

internal fun assertCompleted(
    expected: PersistenceLastOutcome,
    result: PersistenceRequestResult,
) {
    assertEquals(expected, assertInstanceOf(PersistenceRequestResult.Completed::class.java, result).outcome)
}

internal fun assertAwaiting(result: PersistenceRequestResult): PersistenceConfirmationRequest =
    assertInstanceOf(PersistenceRequestResult.AwaitingConfirmation::class.java, result).request

internal fun assertPublished(result: AutosaveRequestResult): RecoveryGeneration =
    assertInstanceOf(AutosaveRequestResult.Published::class.java, result).generation

internal fun assertSaving(phase: PersistenceOperationPhase): PersistenceOperationHandle =
    assertInstanceOf(PersistenceOperationPhase.Saving::class.java, phase).operation

internal fun assertLoading(phase: PersistenceOperationPhase): PersistenceOperationHandle =
    assertInstanceOf(PersistenceOperationPhase.Loading::class.java, phase).operation

internal fun assertSwitching(phase: PersistenceOperationPhase): PersistenceOperationHandle =
    assertInstanceOf(PersistenceOperationPhase.Switching::class.java, phase).operation

internal fun generation(value: Long): RecoveryGeneration =
    when (val result = RecoveryGeneration.create(value)) {
        is RecoveryGenerationResult.Created -> result.generation
        RecoveryGenerationResult.Rejected -> fail("Recovery generation fixture was rejected: $value")
    }

internal fun documentId(character: Char): DocumentId =
    when (val result = DocumentId.create(character.toString().repeat(32))) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> fail("Document ID fixture was rejected: ${result.rejection}")
    }

internal fun created(result: ViewportValueResult<ViewportZoom>): ViewportZoom =
    when (result) {
        is ViewportValueResult.Created -> result.value
        is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: ${result.rejection}")
    }
