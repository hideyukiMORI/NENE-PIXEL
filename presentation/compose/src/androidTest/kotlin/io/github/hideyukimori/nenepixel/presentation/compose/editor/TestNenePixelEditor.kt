package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
internal fun TestNenePixelEditor(
    controller: EditorController,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val persistence = remember(controller, scope) { TestPersistenceHost(controller, scope) }
    LaunchedEffect(persistence) { persistence.initialize() }
    NenePixelEditor(
        renderStates = controller.renderStates,
        persistenceOperations = persistence.workflow.operation,
        autosaveStates = persistence.workflow.autosave,
        callbacks = controller.callbacks,
        persistenceCallbacks = persistence.callbacks,
        language =
            remember {
                AppLanguageControls(
                    MutableStateFlow(AppLanguageSettings(AppLanguage.System, AppLanguageStatus.Ready)),
                    {},
                    {},
                )
            },
        modifier = modifier,
    )
}

private class TestPersistenceHost(
    private val controller: EditorController,
    private val scope: CoroutineScope,
) {
    private val recovery = TestRecoveryRecordPort()
    val workflow =
        EditorPersistenceWorkflow.create(
            controller.runtime,
            TestProjectStoragePort,
            recovery,
            pngExport =
                io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort {
                    io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome.Cancelled
                },
        )
    val callbacks =
        EditorPersistenceCallbacks.create(
            exportPng = {},
            saveAs = { complete { workflow.saveAs() } },
            load = { complete { workflow.load() } },
            createNewDocument = { request -> complete { workflow.createNewDocument(request) } },
            confirm = { request -> complete { workflow.confirm(request) } },
            cancel = { operation ->
                workflow.cancel(operation)
                controller.synchronizeWithRuntime()
            },
            acceptRecovery = {
                workflow.acceptRecovery()
                controller.synchronizeWithRuntime()
            },
            declineRecovery = { complete { workflow.declineRecovery() } },
        )

    suspend fun initialize() {
        workflow.initializeRecovery()
        controller.synchronizeWithRuntime()
    }

    private fun complete(block: suspend () -> Unit) {
        scope.launch {
            block()
            controller.synchronizeWithRuntime()
        }
    }
}

private data object TestProjectStoragePort : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled
}

private class TestRecoveryRecordPort : RecoveryRecordPort {
    private var generation: Long = 0L

    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome {
        generation += 1L
        return RecoveryRetirementOutcome.Retired(generation(generation))
    }

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome {
        generation += 1L
        return RecoveryPublicationOutcome.Published(generation(generation))
    }

    private fun generation(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Invalid test recovery generation: $value")
        }
}
