package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class EditorRuntime private constructor(
    private val documentIdSource: DocumentIdSource,
    public val palette: Palette,
    private val workspaceReducer: WorkspaceReducer,
    initialOwners: RuntimeOwners,
) {
    private val runtimeLock: Any = Any()
    private var owners: RuntimeOwners = initialOwners
    private var coordination: PersistenceCoordination = PersistenceCoordination.initial()
    private val mutablePersistenceOperation: MutableStateFlow<PersistenceOperationProjection> =
        MutableStateFlow(PersistenceProjectionMapper.project(coordination))
    private val mutableAutosave: MutableStateFlow<AutosaveProjection> =
        MutableStateFlow(PersistenceProjectionMapper.projectAutosave(coordination))

    internal val saveOperations: RuntimeSaveOperations = RuntimeSaveOperations(this)
    internal val switchOperations: RuntimeSwitchOperations = RuntimeSwitchOperations(this)
    internal val autosaveOperations: RuntimeAutosaveOperations = RuntimeAutosaveOperations(this)
    internal val recoveryOperations: RuntimeRecoveryOperations = RuntimeRecoveryOperations(this)

    public val state: EditorRuntimeState
        get() = synchronized(runtimeLock) { owners.toState() }

    internal val persistenceOperation: StateFlow<PersistenceOperationProjection> =
        mutablePersistenceOperation.asStateFlow()

    internal val autosaveProjection: StateFlow<AutosaveProjection> = mutableAutosave.asStateFlow()

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(runtimeLock) {
            if (coordination.activeOperation.isSwitching()) {
                CommandResult.Failed(CommandFailure.PersistenceBusy)
            } else {
                executeLocked(command)
            }
        }

    public fun reduce(action: WorkspaceAction): WorkspaceReductionResult =
        synchronized(runtimeLock) {
            if (coordination.activeOperation.isSwitching()) {
                WorkspaceReductionResult.Rejected(
                    owners.workspaceState,
                    WorkspaceActionRejection.PersistenceBusy,
                )
            } else {
                reduceWorkspaceLocked(action)
            }
        }

    internal fun <R> transact(block: (RuntimeTransaction) -> PersistenceTransition<R>): R =
        synchronized(runtimeLock) {
            val transition = block(RuntimeTransaction())
            coordination = transition.next
            applyEffectLocked(transition.effect)
            publishProjectionsLocked()
            transition.result
        }

    internal fun <R> read(block: (RuntimeTransaction) -> R): R =
        synchronized(runtimeLock) { block(RuntimeTransaction()) }

    private fun executeLocked(command: DocumentCommand): CommandResult {
        val result = owners.commandGateway.execute(command)
        if (result is CommandResult.Applied) {
            val commandState = owners.commandGateway.runtimeState
            coordination =
                AutosaveTransitions.recordCapture(
                    coordination,
                    commandState.documentState,
                    commandState.historyPosition,
                )
            publishProjectionsLocked()
        }
        return result
    }

    private fun publishProjectionsLocked() {
        mutablePersistenceOperation.value = PersistenceProjectionMapper.project(coordination)
        mutableAutosave.value = PersistenceProjectionMapper.projectAutosave(coordination)
    }

    private fun reduceWorkspaceLocked(action: WorkspaceAction): WorkspaceReductionResult {
        val result = workspaceReducer.reduce(owners.workspaceState, action)
        owners = owners.copy(workspaceState = result.nextState)
        return result
    }

    private fun applyEffectLocked(effect: RuntimeOwnerEffect) {
        when (effect) {
            RuntimeOwnerEffect.None -> { }

            RuntimeOwnerEffect.CancelPreview -> {
                cancelPreviewLocked()
            }

            is RuntimeOwnerEffect.InstallCleanCheckpoint -> {
                owners = owners.copy(cleanCheckpoint = effect.checkpoint)
            }

            is RuntimeOwnerEffect.ReplaceOwners -> {
                owners = effect.owners
            }
        }
    }

    private fun cancelPreviewLocked() {
        if (owners.workspaceState.preview != null) {
            reduceWorkspaceLocked(WorkspaceAction.CancelGesturePreview)
        }
    }

    internal inner class RuntimeTransaction {
        val coordination: PersistenceCoordination
            get() = this@EditorRuntime.coordination

        fun documentState(): DocumentState = owners.commandGateway.runtimeState.documentState

        fun historyPosition(): HistoryPosition = owners.commandGateway.runtimeState.historyPosition

        fun documentId(): DocumentId = owners.documentId()

        fun switchContext(): SwitchContext =
            SwitchContext(
                source = owners.sourceToken(coordination.runtimeGeneration),
                dirty = owners.isDirty(),
                newDocumentOwners = { request -> RuntimeOwners.create(request.canvas, documentIdSource) },
                loadedOwners = { document -> RuntimeOwners.create(document) },
            )
    }

    public companion object {
        public fun create(
            initialCanvas: CanvasSize,
            palette: Palette,
            documentIdSource: DocumentIdSource,
        ): EditorRuntime =
            EditorRuntime(
                documentIdSource,
                palette,
                WorkspaceReducer.create(palette),
                RuntimeOwners.create(initialCanvas, documentIdSource),
            )
    }
}
