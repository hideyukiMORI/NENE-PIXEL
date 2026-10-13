package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PalettePersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportCapture
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectPersistencePorts
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
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Uses the real runtime admission and workflow; no cross-module access to capture construction. */
internal object PngExportCaptureFixture {
    suspend fun capture(
        document: DocumentState,
        frameId: FrameId = document.frames.first().id,
    ): PngExportCapture {
        val runtime = EditorRuntime.create(document.size, document.definition, DocumentIdSource { document.id })
        var captured: PngExportCapture? = null
        val exporter =
            PngExportPort {
                captured = it
                PngExportOutcome.Cancelled
            }
        val workflow =
            EditorPersistenceWorkflow.create(
                runtime,
                ports(document, exporter),
                TestCoroutineDispatchers().inline,
            )
        check(workflow.initializeRecovery() == RecoveryInitializationResult.Ready) { "Fixture initialization failed" }
        val loaded = workflow.load()
        check(loaded is PersistenceRequestResult.Completed && loaded.outcome == PersistenceLastOutcome.Loaded) {
            "Fixture load did not install its document: $loaded"
        }
        check(runtime.state.documentState == document) { "Fixture installed a different document" }
        val selected = runtime.reduce(WorkspaceAction.SelectFrame(frameId))
        check(selected is WorkspaceReductionResult.Reduced || selected is WorkspaceReductionResult.Unchanged) {
            "Fixture frame selection was refused: $selected"
        }
        check(runtime.state.workspaceState.activeFrameId == frameId) { "Fixture selected a different frame" }
        workflow.exportPng()
        val result = requireNotNull(captured) { "Real runtime refused the export fixture" }
        check(result.document == document && result.frame == document.frameById(frameId)) {
            "Fixture capture differs from its admitted document or frame"
        }
        return result
    }

    private fun ports(
        document: DocumentState,
        exporter: PngExportPort,
    ): PersistencePorts =
        PersistencePorts(
            ProjectPersistencePorts(
                storage = FixtureLoadPort(document),
                recovery = FixtureRecoveryPort,
            ),
            PngPersistencePorts(
                exporter = exporter,
                importer = PngImportPort { PngImportOutcome.Cancelled },
            ),
            PalettePersistencePorts(
                exporter = PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                importer = PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
            ),
            UnderlayPersistencePorts(
                referenceImage = ReferenceImagePort { ReferenceImageOutcome.Cancelled },
                memory = FixtureUnderlayPort,
            ),
        )
}

private class FixtureLoadPort(
    private val document: DocumentState,
) : ProjectStoragePort {
    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document))

    override suspend fun save(document: DocumentState): ProjectSaveOutcome = error("Fixture only loads")

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        error("Fixture only loads")
}

private object FixtureRecoveryPort : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome {
        check(expected == ExpectedRecoveryLineage.Missing) { "Fixture expects missing recovery lineage" }
        val generation = (RecoveryGeneration.create(1L) as RecoveryGenerationResult.Created).generation
        return RecoveryRetirementOutcome.Retired(generation)
    }

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome = error("Fixture only captures")
}

private object FixtureUnderlayPort : UnderlayMemoryPort {
    override suspend fun recall(document: DocumentId): UnderlayRecollection = UnderlayRecollection.Absent

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored
}
