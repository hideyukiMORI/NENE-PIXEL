package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.workspace.ReconcileDocumentLayer
import io.github.hideyukimori.nenepixel.core.application.workspace.ReconcileDocumentPalette
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.application.workspace.isAllowedDuringPaletteSession
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class EditorRuntime private constructor(
    private val documentIdSource: DocumentIdSource,
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

    /** Device memory of underlays (ADR 0034): beside the persistence coordination, never inside it. */
    private var underlayMemoryTracking: UnderlayMemoryTracking = UnderlayMemoryTracking.initial()
    private val mutableUnderlayMemory: MutableStateFlow<UnderlayMemoryProjection> =
        MutableStateFlow(UnderlayMemoryProjection.Settled)

    /** Derived under the runtime lock from the tracking and the installed workspace underlay. */
    private val underlayMemoryProjection: UnderlayMemoryProjection
        get() = underlayMemoryTracking.projection(owners.workspaceState.underlay)

    internal val pngExportOperations: RuntimePngExportOperations = RuntimePngExportOperations(this)
    internal val paletteJsonOperations: RuntimePaletteJsonOperations = RuntimePaletteJsonOperations(this)
    internal val referenceImageOperations: RuntimeReferenceImageOperations =
        RuntimeReferenceImageOperations(this)
    internal val pngImportOperations: RuntimePngImportOperations = RuntimePngImportOperations(this)
    internal val underlayMemoryOperations: RuntimeUnderlayMemoryOperations = RuntimeUnderlayMemoryOperations(this)

    internal val saveOperations: RuntimeSaveOperations = RuntimeSaveOperations(this)
    internal val switchOperations: RuntimeSwitchOperations = RuntimeSwitchOperations(this)
    internal val autosaveOperations: RuntimeAutosaveOperations = RuntimeAutosaveOperations(this)
    internal val recoveryOperations: RuntimeRecoveryOperations = RuntimeRecoveryOperations(this)
    public val paletteOperations: RuntimePaletteOperations = RuntimePaletteOperations(this)

    public val state: EditorRuntimeState
        get() = synchronized(runtimeLock) { owners.toState() }

    internal val persistenceOperation: StateFlow<PersistenceOperationProjection> =
        mutablePersistenceOperation.asStateFlow()

    internal val autosaveProjection: StateFlow<AutosaveProjection> = mutableAutosave.asStateFlow()

    /** Whether the installed work's underlay still has to be recalled from, or published to, the device. */
    public val underlayMemory: StateFlow<UnderlayMemoryProjection> = mutableUnderlayMemory.asStateFlow()

    public fun captureSource(): CommandSourceAdmission =
        synchronized(runtimeLock) { owners.commandGateway.captureSource() }

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(runtimeLock) {
            if (coordination.activeOperation.isSwitching()) {
                CommandResult.Failed(CommandFailure.PersistenceBusy)
            } else if (owners.workspaceState.paletteEditSession != null) {
                CommandResult.Failed(CommandFailure.PaletteSessionActive)
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
            } else if (owners.workspaceState.paletteEditSession != null && !action.isAllowedDuringPaletteSession()) {
                WorkspaceReductionResult.Rejected(
                    owners.workspaceState,
                    WorkspaceActionRejection.PaletteSessionActive,
                )
            } else {
                reduceWorkspaceLocked(action)
            }
        }

    internal fun <R> transact(block: (RuntimeTransaction) -> PersistenceTransition<R>): R =
        synchronized(runtimeLock) {
            val tracking = underlayMemoryTracking
            val transition = block(RuntimeTransaction())
            coordination = transition.next
            applyEffectLocked(transition.effect)
            publishProjectionsLocked()
            // An installation or a memory operation replaced the tracking (ADR 0034).
            if (underlayMemoryTracking !== tracking) {
                mutableUnderlayMemory.value = underlayMemoryProjection
            }
            transition.result
        }

    internal fun <R> read(block: (RuntimeTransaction) -> R): R =
        synchronized(runtimeLock) { block(RuntimeTransaction()) }

    private fun executeLocked(command: DocumentCommand): CommandResult {
        val result = owners.commandGateway.execute(command)
        if (result is CommandResult.Applied) {
            reconcileSelectionLocked(command, result)
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

    private fun reconcileSelectionLocked(
        command: DocumentCommand,
        result: CommandResult.Applied,
    ) {
        val definition = owners.commandGateway.runtimeState.documentState.definition
        val nextIndex =
            PaletteSelectionPolicy.afterApplied(
                owners.workspaceState.activePaletteIndex,
                definition,
                command,
                result.changeSet.paletteTransition,
            )
        if (nextIndex != null) {
            val recent =
                PaletteSelectionPolicy.recentAfterApplied(
                    owners.workspaceState.quickSelection.recent,
                    definition,
                    command,
                )
            reduceWorkspaceLocked(ReconcileDocumentPalette(nextIndex, recent))
        }
        reconcileLayerLocked(result)
    }

    /** Follows the active layer, then cancels a gesture whose captured layer is gone or hidden (ADR 0030). */
    private fun reconcileLayerLocked(result: CommandResult.Applied) {
        val document = owners.commandGateway.runtimeState.documentState
        val current = owners.workspaceState.activeLayerId
        LayerSelectionPolicy.afterApplied(current, document, result.changeSet.structure)?.let { layerId ->
            reduceWorkspaceLocked(ReconcileDocumentLayer(layerId))
        }
        val gesture = owners.workspaceState.preview
        if (gesture != null && LayerSelectionPolicy.gestureTargetLost(gesture.layerId, document)) {
            reduceWorkspaceLocked(WorkspaceAction.CancelGesturePreview)
        }
    }

    private fun publishProjectionsLocked() {
        mutablePersistenceOperation.value = PersistenceProjectionMapper.project(coordination)
        mutableAutosave.value = PersistenceProjectionMapper.projectAutosave(coordination)
    }

    private fun reduceWorkspaceLocked(action: WorkspaceAction): WorkspaceReductionResult {
        val result = workspaceReducer.reduce(owners.workspaceState, action, owners.commandGateway.captureSource())
        owners = owners.copy(workspaceState = result.nextState)
        // Only an underlay action touches the memory; every other reduction pays this one type check (ADR 0034).
        if (action is WorkspaceAction.ReferenceUnderlayAction) {
            if (result is WorkspaceReductionResult.Reduced) {
                underlayMemoryTracking = underlayMemoryTracking.underlayReduced()
            }
            mutableUnderlayMemory.value = underlayMemoryProjection
        }
        return result
    }

    private fun applyEffectLocked(effect: RuntimeOwnerEffect) {
        when (effect) {
            RuntimeOwnerEffect.None -> { }

            RuntimeOwnerEffect.CancelPreview -> {
                if (owners.workspaceState.preview != null) {
                    reduceWorkspaceLocked(WorkspaceAction.CancelGesturePreview)
                }
            }

            is RuntimeOwnerEffect.InstallCleanCheckpoint -> {
                owners = owners.copy(cleanCheckpoint = effect.checkpoint)
            }

            is RuntimeOwnerEffect.ReplaceOwners -> {
                // The departing work's unpublished underlay is captured before it is replaced (ADR 0034).
                underlayMemoryTracking =
                    underlayMemoryTracking.installed(owners.documentId(), owners.workspaceState.underlay)
                // Session-only editor choices survive document replacement (ADR 0020, ADR 0026).
                val carried = owners.workspaceState
                val source = effect.owners.commandGateway.captureSource()
                val withAppearance =
                    workspaceReducer
                        .reduce(effect.owners.workspaceState, WorkspaceAction.SetAppearance(carried.appearance), source)
                        .nextState
                val withWindow =
                    workspaceReducer
                        .reduce(withAppearance, WorkspaceAction.SetActualSizeWindow(carried.actualSizeWindow), source)
                        .nextState
                // A newly installed document starts on its top layer (ADR 0030).
                val topLayer = LayerSelectionPolicy.onInstall(source.document)
                val workspace =
                    workspaceReducer.reduce(withWindow, ReconcileDocumentLayer(topLayer), source).nextState
                owners = effect.owners.copy(workspaceState = workspace)
            }
        }
    }

    internal inner class RuntimeTransaction {
        val coordination: PersistenceCoordination
            get() = this@EditorRuntime.coordination

        /** The underlay memory bookkeeping; `transact` publishes its projection when it is replaced. */
        var underlayMemoryTracking: UnderlayMemoryTracking
            get() = this@EditorRuntime.underlayMemoryTracking
            set(value) {
                this@EditorRuntime.underlayMemoryTracking = value
            }

        fun documentState(): DocumentState = owners.commandGateway.runtimeState.documentState

        fun captureSource(): CommandSourceAdmission = owners.commandGateway.captureSource()

        fun historyPosition(): HistoryPosition = owners.commandGateway.runtimeState.historyPosition

        fun documentId(): DocumentId = owners.documentId()

        fun paletteSessionActive(): Boolean = owners.workspaceState.paletteEditSession != null

        fun workspaceState(): WorkspaceState = owners.workspaceState

        /** Reduces inside the lock without the `reduce` gate; callers own the admission decision. */
        fun reduceWorkspace(action: WorkspaceAction): WorkspaceReductionResult = reduceWorkspaceLocked(action)

        /** Executes inside the lock without the `execute` gate; callers own the admission decision. */
        fun executeCommand(command: DocumentCommand): CommandResult = executeLocked(command)

        fun switchContext(): SwitchContext {
            val definition = documentState().definition
            return SwitchContext(
                source = owners.sourceToken(coordination.runtimeGeneration),
                dirty = owners.isDirty(),
                newDocumentOwners = { request -> RuntimeOwners.create(request.canvas, definition, documentIdSource) },
                loadedOwners = { document -> RuntimeOwners.create(document) },
                derivedOwners = { preview -> RuntimeOwners.createDerived(preview, documentIdSource) },
                importedOwners = { plan -> RuntimeOwners.createImported(plan, documentIdSource) },
            )
        }
    }

    public companion object {
        public fun create(
            initialCanvas: CanvasSize,
            definition: PaletteDefinition,
            documentIdSource: DocumentIdSource,
        ): EditorRuntime =
            EditorRuntime(
                documentIdSource,
                WorkspaceReducer.create(),
                RuntimeOwners.create(initialCanvas, definition, documentIdSource),
            )
    }
}
