package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/** A real runtime and workflow whose underlay memory port records every call (ADR 0034). */
internal class UnderlayMemorySchedulerFixture {
    private val dispatchers = TestCoroutineDispatchers()
    private val runtime = createEditorRuntime()
    val port = RecordingUnderlayMemoryPort()
    val time = ManualQuietTime()
    var recalledCount: Int = 0
        private set
    private val workflow =
        EditorPersistenceWorkflow.create(
            runtime,
            PersistencePorts(
                ClosedProjectStorage,
                RetiringRecoveryRecord,
                PngExportPort { PngExportOutcome.Cancelled },
                PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
                ReferenceImagePort { ReferenceImageOutcome.Cancelled },
                PngImportPort { PngImportOutcome.Cancelled },
                port,
            ),
            dispatchers.inline,
        )
    val scheduler =
        UnderlayMemoryScheduler(
            workflow.underlayMemory,
            { recalledCount += 1 },
            UnderlayMemoryPolicy(quietMillis = QUIET_MILLIS),
            time::await,
        )

    suspend fun launchIn(scope: CoroutineScope): Job {
        check(workflow.initializeRecovery() == RecoveryInitializationResult.Ready)
        return scheduler.launchIn(scope).also { settle() }
    }

    /** Installs a new work, which leaves the installed work's memory `RecallPending`. */
    suspend fun installNewWork() {
        workflow.createNewDocument(NewDocumentRequest.create("4", "4"))
        settle()
    }

    /** Places a fresh underlay that differs from every earlier one by its opacity. */
    suspend fun place(opacityStep: Int = 0): ReferenceUnderlay {
        val underlay =
            ReferenceUnderlay
                .placed(image(), runtime.state.documentState.size)
                .withOpacity(UnderlayOpacity.create(UnderlayOpacity.MIN.alpha + opacityStep))
        set(underlay)
        return underlay
    }

    suspend fun set(underlay: ReferenceUnderlay) {
        runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
        settle()
    }

    suspend fun advanceBy(millis: Long) {
        time.advanceBy(millis)
        settle()
    }

    suspend fun settle() {
        delay(SETTLE_MILLIS)
    }

    companion object {
        const val QUIET_MILLIS: Long = 500L
        private const val SETTLE_MILLIS: Long = 20L
    }
}

internal fun remembered(underlay: ReferenceUnderlay): RecordedUnderlayCall =
    RecordedUnderlayCall.Remember(RememberedUnderlay.of(underlay))

private fun image(): ReferenceImage =
    when (val result = ReferenceImage.create(2, 2, IntArray(4))) {
        is ReferenceImageResult.Created -> result.image
        else -> error("Underlay scheduler fixture image was rejected: $result")
    }

private object ClosedProjectStorage : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled
}

private object RetiringRecoveryRecord : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        when (val result = RecoveryGeneration.create(1L)) {
            is RecoveryGenerationResult.Created -> RecoveryRetirementOutcome.Retired(result.generation)
            RecoveryGenerationResult.Rejected -> RecoveryRetirementOutcome.GenerationExhausted
        }

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome = RecoveryPublicationOutcome.GenerationExhausted
}
