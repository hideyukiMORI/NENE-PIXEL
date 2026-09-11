package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import kotlinx.coroutines.flow.StateFlow

public class EditorPersistenceWorkflow private constructor(
    private val runtime: EditorRuntime,
    private val flows: PersistenceFlows,
) {
    public val operation: StateFlow<PersistenceOperationProjection>
        get() = runtime.persistenceOperation

    public val autosave: StateFlow<AutosaveProjection>
        get() = runtime.autosaveProjection

    public suspend fun initializeRecovery(): RecoveryInitializationResult = flows.save.initializeRecovery()

    public suspend fun saveAs(): PersistenceRequestResult = flows.save.saveAs()

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
            projectStorage: ProjectStoragePort,
            recoveryRecord: RecoveryRecordPort,
        ): EditorPersistenceWorkflow {
            val autosave = PersistenceAutosaveFlow(runtime.autosaveOperations, recoveryRecord)
            return EditorPersistenceWorkflow(
                runtime,
                PersistenceFlows(
                    PersistenceSaveFlow(runtime.saveOperations, projectStorage, recoveryRecord, autosave),
                    PersistenceSwitchFlow(runtime.switchOperations, projectStorage, recoveryRecord, autosave),
                    autosave,
                    PersistenceRecoveryFlow(runtime.recoveryOperations, recoveryRecord),
                ),
            )
        }
    }
}

internal class PersistenceFlows(
    val save: PersistenceSaveFlow,
    val switch: PersistenceSwitchFlow,
    val autosave: PersistenceAutosaveFlow,
    val recovery: PersistenceRecoveryFlow,
)

internal fun identityExhaustedResult(): PersistenceRequestResult.Completed =
    PersistenceRequestResult.Completed(PersistenceLastOutcome.Failed(PersistenceFailure.IdentityExhausted))
