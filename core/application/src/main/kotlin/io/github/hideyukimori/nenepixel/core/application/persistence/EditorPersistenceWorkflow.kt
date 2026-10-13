package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow

public class EditorPersistenceWorkflow private constructor(
    private val runtime: EditorRuntime,
    private val flows: PersistenceFlows,
    private val interchange: PersistenceInterchangeFlows,
    public val underlayMemory: UnderlayMemoryWorkflow,
) {
    public val operation: StateFlow<PersistenceOperationProjection>
        get() = runtime.persistenceOperation

    public val autosave: StateFlow<AutosaveProjection>
        get() = runtime.autosaveProjection

    public val legacyImport: LegacyImportWorkflow = LegacyImportWorkflow(flows.switch, flows.recovery)

    public val paletteJson: PaletteJsonWorkflow = PaletteJsonWorkflow(interchange.palette)

    public val referenceImage: ReferenceImageWorkflow = ReferenceImageWorkflow(interchange.referenceImage)

    public val pngImport: PngImportWorkflow = PngImportWorkflow(interchange.pngImport, flows.switch)

    public suspend fun initializeRecovery(): RecoveryInitializationResult = flows.save.initializeRecovery()

    public suspend fun saveAs(): PersistenceRequestResult = flows.save.saveAs()

    public suspend fun exportPng(): PersistenceRequestResult = interchange.pngExport.exportPng()

    public suspend fun load(): PersistenceRequestResult = flows.switch.load()

    public suspend fun createNewDocument(request: NewDocumentRequestResult): PersistenceRequestResult =
        flows.switch.createNewDocument(request)

    public suspend fun confirm(request: PersistenceConfirmationRequest): PersistenceRequestResult =
        flows.switch.confirm(request)

    public fun cancel(operation: PersistenceOperationHandle): PersistenceCancellationResult =
        flows.switch.cancel(operation)

    public suspend fun publishLatestCapture(): AutosaveRequestResult = flows.autosave.publishLatestCapture()

    public fun acceptRecovery(): PersistenceRequestResult = flows.recovery.acceptRecovery()

    public suspend fun declineRecovery(): PersistenceRequestResult = flows.recovery.declineRecovery()

    public companion object {
        public fun create(
            runtime: EditorRuntime,
            ports: PersistencePorts,
            conversionDispatcher: CoroutineDispatcher,
        ): EditorPersistenceWorkflow {
            val autosave = PersistenceAutosaveFlow(runtime.autosaveOperations, ports.recoveryRecord)
            return EditorPersistenceWorkflow(
                runtime,
                PersistenceFlows(
                    PersistenceSaveFlow(
                        runtime.saveOperations,
                        ports,
                        autosave,
                        conversionDispatcher,
                    ),
                    PersistenceSwitchFlow(
                        runtime.switchOperations,
                        ports,
                        autosave,
                        conversionDispatcher,
                    ),
                    autosave,
                    PersistenceRecoveryFlow(runtime.recoveryOperations, ports.recoveryRecord),
                ),
                PersistenceInterchangeFlows(
                    PersistencePngExportFlow(runtime.pngExportOperations, ports.pngExport, autosave),
                    PersistencePaletteJsonFlow(
                        runtime.paletteJsonOperations,
                        ports.paletteJsonExport,
                        ports.paletteJsonImport,
                        autosave,
                    ),
                    PersistenceReferenceImageFlow(runtime.referenceImageOperations, ports.referenceImage, autosave),
                    PersistencePngImportFlow(
                        runtime.pngImportOperations,
                        ports.pngImport,
                        autosave,
                        conversionDispatcher,
                    ),
                ),
                UnderlayMemoryWorkflow(runtime.underlayMemoryOperations, ports.underlayMemory, runtime.underlayMemory),
            )
        }
    }
}

private class PersistenceInterchangeFlows(
    val pngExport: PersistencePngExportFlow,
    val palette: PersistencePaletteJsonFlow,
    val referenceImage: PersistenceReferenceImageFlow,
    val pngImport: PersistencePngImportFlow,
)

internal class PersistenceFlows(
    val save: PersistenceSaveFlow,
    val switch: PersistenceSwitchFlow,
    val autosave: PersistenceAutosaveFlow,
    val recovery: PersistenceRecoveryFlow,
)

internal fun identityExhaustedResult(): PersistenceRequestResult.Completed =
    PersistenceRequestResult.Completed(PersistenceLastOutcome.Failed(PersistenceFailure.IdentityExhausted))
