package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
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

    internal val saveOperations: RuntimeSaveOperations = RuntimeSaveOperations(this)
    internal val switchOperations: RuntimeSwitchOperations = RuntimeSwitchOperations(this)

    public val state: EditorRuntimeState
        get() = synchronized(runtimeLock) { owners.toState() }

    internal val persistenceOperation: StateFlow<PersistenceOperationProjection> =
        mutablePersistenceOperation.asStateFlow()

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(runtimeLock) {
            if (coordination.activeOperation.isSwitching()) {
                CommandResult.Failed(CommandFailure.PersistenceBusy)
            } else {
                owners.commandGateway.execute(command)
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
            mutablePersistenceOperation.value = PersistenceProjectionMapper.project(coordination)
            transition.result
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
