package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import kotlinx.coroutines.flow.StateFlow

public class EditorPersistenceWorkflow private constructor(
    private val runtime: EditorRuntime,
    private val saveFlow: PersistenceSaveFlow,
    private val switchFlow: PersistenceSwitchFlow,
) {
    public val operation: StateFlow<PersistenceOperationProjection>
        get() = runtime.persistenceOperation

    public suspend fun initializeRecovery(): RecoveryInitializationResult = saveFlow.initializeRecovery()

    public suspend fun saveAs(): PersistenceRequestResult = saveFlow.saveAs()

    public suspend fun load(): PersistenceRequestResult = switchFlow.load()

    public suspend fun createNewDocument(request: NewDocumentRequestResult): PersistenceRequestResult =
        switchFlow.createNewDocument(request)

    public suspend fun confirm(request: PersistenceConfirmationRequest): PersistenceRequestResult =
        switchFlow.confirm(request)

    public fun cancel(operation: PersistenceOperationHandle): PersistenceCancellationResult =
        switchFlow.cancel(operation)

    public companion object {
        public fun create(
            runtime: EditorRuntime,
            projectStorage: ProjectStoragePort,
            recoveryRecord: RecoveryRecordPort,
        ): EditorPersistenceWorkflow =
            EditorPersistenceWorkflow(
                runtime,
                PersistenceSaveFlow(runtime.saveOperations, projectStorage, recoveryRecord),
                PersistenceSwitchFlow(runtime.switchOperations, projectStorage, recoveryRecord),
            )
    }
}

internal fun identityExhaustedResult(): PersistenceRequestResult.Completed =
    PersistenceRequestResult.Completed(PersistenceLastOutcome.Failed(PersistenceFailure.IdentityExhausted))
