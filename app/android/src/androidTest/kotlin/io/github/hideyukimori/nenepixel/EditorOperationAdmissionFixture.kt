package io.github.hideyukimori.nenepixel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PalettePersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
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
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.measurement.EmptyUnderlayMemoryPort
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageSettings
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppVersionDisplay
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorSettingsInputs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicInteger

/** Real app callbacks/schedulers; every external capability is synthetic and memory-only (#244). */
internal class EditorOperationAdmissionFixture {
    private val runtime = createEditorRuntime()
    private val controller = EditorController.create(runtime)
    private val recovery = AdmissionRecoveryPort()
    private val exporter = AdmissionExportPort()
    private val storage = AdmissionStoragePort()
    private val dispatchers = TestCoroutineDispatchers()
    private val workflow = EditorPersistenceWorkflow.create(runtime, ports(), dispatchers.parallel)

    lateinit var model: EditorRuntimeViewModel
        private set

    val attached: Boolean get() = ::model.isInitialized
    val publicationEntered: Boolean get() = recovery.gate.entered
    val exportEntered: Boolean get() = exporter.gate.entered
    val cleanupEntered: Boolean get() = exporter.cleanup.entered
    val exportCalls: Int get() = exporter.calls.get()
    val saveCalls: Int get() = storage.calls.get()
    val publishedDocument: DocumentState? get() = recovery.capture
    val exportedDocument: DocumentState? get() = exporter.capture?.document
    val savedDocument: DocumentState? get() = storage.capture

    val settings =
        EditorSettingsInputs(
            AppLanguageControls(
                MutableStateFlow(AppLanguageSettings(AppLanguage.System, AppLanguageStatus.Ready)),
                {},
                {},
            ),
            AppVersionDisplay.Available("synthetic-admission", 1L),
        )

    fun attach(activity: ComponentActivity) {
        val factory =
            viewModelFactory {
                initializer { EditorRuntimeViewModel(runtime, controller, ProjectPickerBroker(), workflow) }
            }
        model = ViewModelProvider(activity, factory)[EditorRuntimeViewModel::class.java]
    }

    fun releasePublication() {
        recovery.gate.release()
    }

    fun releaseExport() {
        exporter.gate.release()
        releaseCleanup()
    }

    fun releaseCleanup() {
        exporter.cleanup.release()
    }

    fun installUnderlay() {
        val image =
            when (val created = ReferenceImage.create(1, 1, intArrayOf(0x00ff00ff))) {
                is ReferenceImageResult.Created -> created.image
                is ReferenceImageResult.Rejected -> error("Synthetic image was rejected")
            }
        runtime.reduce(
            WorkspaceAction.SetReferenceUnderlay(ReferenceUnderlay.placed(image, runtime.state.documentState.size)),
        )
        controller.synchronizeWithRuntime()
    }

    fun releaseAll() {
        releasePublication()
        releaseExport()
    }

    fun report(
        stage: String,
        visibleStatus: String,
    ) {
        val status =
            Bundle().apply {
                putString("admissionStage", stage)
                putInt("exportCalls", exportCalls)
                putInt("saveCalls", saveCalls)
                putString("availability", model.operationAvailability.value.toString())
                putString(
                    "phase",
                    model.persistenceOperations.value.phase
                        .toString(),
                )
                putString(
                    "outcome",
                    model.persistenceOperations.value.lastOutcome
                        .toString(),
                )
                putString("autosave", model.autosaveStates.value.toString())
                putString("visibleStatus", visibleStatus)
            }
        InstrumentationRegistry.getInstrumentation().sendStatus(STATUS_CODE, status)
        println("ADMISSION $stage exports=$exportCalls saves=$saveCalls status=$visibleStatus")
    }

    private fun ports(): PersistencePorts =
        PersistencePorts(
            ProjectPersistencePorts(storage, recovery),
            PngPersistencePorts(exporter, PngImportPort { PngImportOutcome.Cancelled }),
            PalettePersistencePorts(
                PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
            ),
            UnderlayPersistencePorts(ReferenceImagePort { ReferenceImageOutcome.Cancelled }, EmptyUnderlayMemoryPort),
        )

    private companion object {
        const val STATUS_CODE: Int = 2
    }
}

/** A suspended external port, not an Android writer or a durability oracle. */
private class AdmissionRecoveryPort : RecoveryRecordPort {
    val gate = AdmissionPortGate()
    var capture: DocumentState? = null
        private set
    private var record: RecoveryInspection = RecoveryInspection.Missing

    override suspend fun inspect(): RecoveryInspection = record

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome {
        assertEquals(ExpectedRecoveryLineage.Missing, expected)
        capture = document
        gate.hold()
        val generation =
            when (val created = RecoveryGeneration.create(1L)) {
                is RecoveryGenerationResult.Created -> created.generation
                RecoveryGenerationResult.Rejected -> error("Synthetic generation was rejected")
            }
        record = RecoveryInspection.Candidate(generation, DocumentImportSource.Current(document))
        return RecoveryPublicationOutcome.Published(generation)
    }

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        error("Cancelled synthetic file operations must not retire recovery")
}

private class AdmissionExportPort : PngExportPort {
    val gate = AdmissionPortGate()
    val cleanup = AdmissionPortGate()
    val calls = AtomicInteger()
    var capture: PngExportCapture? = null
        private set

    override suspend fun export(capture: PngExportCapture): PngExportOutcome {
        this.capture = capture
        calls.incrementAndGet()
        try {
            gate.hold()
            return PngExportOutcome.Cancelled
        } finally {
            withContext(NonCancellable) { cleanup.hold() }
        }
    }
}

private class AdmissionStoragePort : ProjectStoragePort {
    val calls = AtomicInteger()
    var capture: DocumentState? = null
        private set

    override suspend fun save(document: DocumentState): ProjectSaveOutcome {
        capture = document
        calls.incrementAndGet()
        return ProjectSaveOutcome.Cancelled
    }

    override suspend fun load(): ProjectLoadOutcome = error("Admission fixture never loads a project")

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        error("Admission fixture never copies a legacy source")
}

private class AdmissionPortGate {
    private val entry = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()
    val entered: Boolean get() = entry.isCompleted

    suspend fun hold() {
        entry.complete(Unit)
        withTimeout(GATE_BUDGET_MILLIS) { released.await() }
    }

    fun release() {
        released.complete(Unit)
    }

    private companion object {
        // Bounds one deliberately held UI phase; individual observations retain their 10-second budget.
        const val GATE_BUDGET_MILLIS: Long = 90_000L
    }
}
