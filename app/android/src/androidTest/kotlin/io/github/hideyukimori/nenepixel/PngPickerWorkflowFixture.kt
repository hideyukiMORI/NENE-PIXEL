package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PalettePersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayPersistencePorts
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.measurement.EmptyUnderlayMemoryPort
import kotlinx.coroutines.CoroutineDispatcher

/** A fresh canonical runtime; the export-only test never calls storage or the unrelated pickers. */
internal object PngPickerWorkflowFixture {
    fun create(
        storage: ProjectStoragePort,
        exporter: PngExportPort,
        dispatcher: CoroutineDispatcher,
    ): EditorPersistenceWorkflow =
        EditorPersistenceWorkflow.create(
            createEditorRuntime(),
            PersistencePorts(
                ProjectPersistencePorts(
                    storage,
                    MissingPngRecovery,
                ),
                PngPersistencePorts(
                    exporter,
                    PngImportPort { PngImportOutcome.Cancelled },
                ),
                PalettePersistencePorts(
                    PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                    PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
                ),
                UnderlayPersistencePorts(
                    ReferenceImagePort { ReferenceImageOutcome.Cancelled },
                    EmptyUnderlayMemoryPort,
                ),
            ),
            dispatcher,
        )
}

private object MissingPngRecovery : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        error("Export cancellation fixture never retires recovery")

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome = error("Clean export fixture never publishes recovery")
}
