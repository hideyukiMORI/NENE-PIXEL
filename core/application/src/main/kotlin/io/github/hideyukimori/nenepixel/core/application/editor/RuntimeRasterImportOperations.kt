package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.LayerImportMode
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.RasterImportOption

/** Applies one captured pending layer form; UI cannot substitute a newer source or current focus. */
public class RuntimeRasterImportOperations internal constructor(
    private val runtime: EditorRuntime,
) {
    public fun applyLayerImport(mode: LayerImportMode): RasterImportApplyResult =
        runtime.transact { transaction ->
            val result = apply(transaction, mode)
            PersistenceTransition(transaction.coordination, result)
        }

    private fun apply(
        transaction: EditorRuntime.RuntimeTransaction,
        mode: LayerImportMode,
    ): RasterImportApplyResult {
        val state = transaction.workspaceState()
        val pending = state.pendingImport
        val form =
            when (mode) {
                LayerImportMode.Appending -> pending?.appending
                LayerImportMode.Converting -> pending?.converting
            }
        return when {
            transaction.coordination.activeOperation.isSwitching() -> {
                rejected(WorkspaceActionRejection.PersistenceBusy)
            }

            state.paletteEditSession != null -> {
                rejected(WorkspaceActionRejection.PaletteSessionActive)
            }

            pending == null -> {
                rejected(WorkspaceActionRejection.NoPendingRasterImport)
            }

            form !is RasterImportOption.Available -> {
                rejected(WorkspaceActionRejection.RasterImportFormUnavailable(mode))
            }

            else -> {
                val target = pending.target
                val frame = requireNotNull(target.admission.document.frameById(target.frameId))
                require(frame.layers.any { it.id == target.anchorLayerId })
                val command = ImportLayerCommand.create(target.admission, target.anchorLayerId, form.plan)
                finish(transaction, transaction.executeCommand(command))
            }
        }
    }

    private fun finish(
        transaction: EditorRuntime.RuntimeTransaction,
        result: CommandResult,
    ): RasterImportApplyResult =
        when (result) {
            is CommandResult.Applied -> {
                transaction.reduceWorkspace(WorkspaceAction.ClearPendingRasterImport)
                RasterImportApplyResult.Applied
            }

            is CommandResult.Rejected -> {
                RasterImportApplyResult.CommandRejected(result.reason)
            }

            is CommandResult.Failed -> {
                RasterImportApplyResult.CommandFailed(result.failure)
            }
        }

    private fun rejected(reason: WorkspaceActionRejection): RasterImportApplyResult =
        RasterImportApplyResult.Rejected(reason)
}
