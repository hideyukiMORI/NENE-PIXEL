package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryWorkflow
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
internal fun TestNenePixelEditor(
    controller: EditorController,
    modifier: Modifier = Modifier,
    configuration: TestPersistenceConfiguration = TestPersistenceConfiguration(),
    dispatchers: TestCoroutineDispatchers = TestCoroutineDispatchers(),
) {
    val persistence = rememberTestPersistenceHost(controller, configuration, dispatchers.inline)
    val underlayMemory = configuration.underlayMemory
    LaunchedEffect(persistence) { persistence.initialize() }
    DisposableEffect(persistence, underlayMemory) {
        val requester: UnderlayMemoryRequester = persistence::requestUnderlayMemory
        underlayMemory?.attach(requester)
        onDispose { underlayMemory?.detach(requester) }
    }
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
        version = AppVersionDisplay.Available("0.1.0", 1L),
        modifier = modifier,
    )
}

/** Owns the same composition scope and effective port keys as the editor fixture. */
@Composable
private fun rememberTestPersistenceHost(
    controller: EditorController,
    configuration: TestPersistenceConfiguration,
    dispatcher: CoroutineDispatcher,
): TestPersistenceHost {
    val scope = rememberCoroutineScope()
    val document = configuration.document
    val images = configuration.images
    val underlayMemoryPort = configuration.underlayMemory?.port ?: EmptyUnderlayMemoryPort
    return remember(
        controller,
        scope,
        document.storage,
        document.presets,
        configuration.paletteJson,
        images.referenceImage,
        images.pngImport,
        underlayMemoryPort,
        dispatcher,
    ) {
        TestPersistenceHost(
            controller,
            scope,
            configuration,
            dispatcher,
        )
    }
}

private fun defaultPresets(controller: EditorController): LegacyPalettePresets =
    controller.renderState.definition.let { definition ->
        LegacyPalettePresets(definition, definition, definition)
    }

/** Palette JSON file ports for one fixture; both default to a cancelled picker. */
internal data class TestPaletteJsonPorts(
    val export: PaletteJsonExportPort = PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
    val import: PaletteJsonImportPort = PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
)

private class TestPersistenceHost(
    private val controller: EditorController,
    private val scope: CoroutineScope,
    configuration: TestPersistenceConfiguration,
    dispatcher: CoroutineDispatcher,
) {
    private val document = configuration.document
    private val images = configuration.images
    private val presets = document.presets ?: defaultPresets(controller)
    val workflow =
        EditorPersistenceWorkflow.create(
            controller.runtime,
            PersistencePorts(
                document.storage,
                document.recovery ?: TestRecoveryRecordPort(),
                io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort {
                    io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome.Cancelled
                },
                configuration.paletteJson.export,
                configuration.paletteJson.import,
                images.referenceImage,
                images.pngImport,
                configuration.underlayMemory?.port ?: EmptyUnderlayMemoryPort,
            ),
            dispatcher,
        )
    val callbacks =
        EditorPersistenceCallbacks.create(
            ProjectFileCallbacks(
                exchange =
                    FileExchangeCallbacks(
                        exportPng = {},
                        exportPaletteJson = { complete { workflow.paletteJson.export() } },
                        importPaletteJson = { complete { workflow.paletteJson.import() } },
                        pickReferenceImage = { complete { workflow.referenceImage.pick() } },
                        importPng = { complete { workflow.pngImport.pick() } },
                        openPngAsNewWork = { complete { workflow.pngImport.openAsNewWork() } },
                    ),
                saveAs = { complete { workflow.saveAs() } },
                load = { complete { workflow.load() } },
                createNewDocument = { request -> complete { workflow.createNewDocument(request) } },
            ),
            PersistenceDecisionCallbacks(
                confirm = { request -> complete { workflow.confirm(request) } },
                cancel = { operation: PersistenceOperationHandle ->
                    workflow.cancel(operation)
                    controller.synchronizeWithRuntime()
                },
                acceptRecovery = {
                    workflow.acceptRecovery()
                    controller.synchronizeWithRuntime()
                },
                declineRecovery = { complete { workflow.declineRecovery() } },
            ),
            LegacyConversionCallbacks(
                copyOriginal = { operation: PersistenceOperationHandle ->
                    complete { workflow.legacyImport.copySource(operation) }
                },
                preview = { operation: PersistenceOperationHandle, definition: PaletteDefinition ->
                    complete { workflow.legacyImport.previewSource(operation, definition) }
                },
                accept = { handle: LegacyReductionHandle ->
                    complete { workflow.legacyImport.acceptReduction(handle) }
                },
                declineRecovery = { operation: PersistenceOperationHandle ->
                    complete { workflow.legacyImport.declineRecovery(operation) }
                },
            ),
            presets = presets,
        )

    suspend fun initialize() {
        workflow.initializeRecovery()
        controller.synchronizeWithRuntime()
    }

    /** Stands in for the app's underlay memory scheduler: runs [request] only when a test asks (ADR 0034). */
    fun requestUnderlayMemory(request: suspend (UnderlayMemoryWorkflow) -> Unit) {
        complete { request(workflow.underlayMemory) }
    }

    private fun complete(block: suspend () -> Unit) {
        scope.launch {
            block()
            controller.synchronizeWithRuntime()
        }
    }
}

private class TestRecoveryRecordPort(
    private val inspection: RecoveryInspection = RecoveryInspection.Missing,
) : RecoveryRecordPort {
    private var generation: Long = 0L

    override suspend fun inspect(): RecoveryInspection = inspection

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
