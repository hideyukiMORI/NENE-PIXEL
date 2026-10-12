package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.TransferPixelsCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.BeginSelectionSession
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/** Atomic layer-target entry and Apply; pointer geometry remains in the workspace reducer (ADR 0039). */
public class RuntimeSelectionOperations internal constructor(
    private val runtime: EditorRuntime,
) {
    public fun beginSelection(layerId: LayerId): WorkspaceReductionResult =
        runtime.transact { transaction ->
            val result = begin(transaction, layerId)
            PersistenceTransition(transaction.coordination, result)
        }

    public fun applySelection(): SelectionApplyResult =
        runtime.transact { transaction ->
            val result = apply(transaction)
            PersistenceTransition(transaction.coordination, result)
        }

    private fun begin(
        transaction: EditorRuntime.RuntimeTransaction,
        layerId: LayerId,
    ): WorkspaceReductionResult {
        val workspace = transaction.workspaceState()
        val layer = transaction.documentState().layers.firstOrNull { it.id == layerId }
        val refusal =
            when {
                transaction.coordination.activeOperation.isSwitching() -> {
                    WorkspaceActionRejection.PersistenceBusy
                }

                workspace.paletteEditSession != null -> {
                    WorkspaceActionRejection.PaletteSessionActive
                }

                workspace.pendingImport != null -> {
                    WorkspaceActionRejection.SelectionRejected(SelectionRejection.PendingImport)
                }

                layer == null -> {
                    WorkspaceActionRejection.LayerNotFound(layerId)
                }

                layer.visibility == LayerVisibility.Hidden -> {
                    WorkspaceActionRejection.ActiveLayerHidden(layerId)
                }

                else -> {
                    null
                }
            }
        return if (refusal != null) {
            WorkspaceReductionResult.Rejected(workspace, refusal)
        } else {
            enter(transaction, layerId)
        }
    }

    private fun enter(
        transaction: EditorRuntime.RuntimeTransaction,
        layerId: LayerId,
    ): WorkspaceReductionResult {
        transaction.reduceWorkspace(WorkspaceAction.CancelGesturePreview)
        transaction.workspaceState().underlay?.let { underlay ->
            if (underlay.interaction == UnderlayInteraction.Adjusting) {
                transaction.reduceWorkspace(WorkspaceAction.SetReferenceUnderlay(underlay.rested()))
            }
        }
        transaction.reduceWorkspace(WorkspaceAction.CancelQuickSelect)
        transaction.reduceWorkspace(WorkspaceAction.DisarmEyedropper)
        transaction.reduceWorkspace(WorkspaceAction.SelectLayer(layerId))
        return transaction.reduceWorkspace(BeginSelectionSession(transaction.captureSource()))
    }

    private fun apply(transaction: EditorRuntime.RuntimeTransaction): SelectionApplyResult {
        val session = transaction.workspaceState().selectionSession
        val transfer = session?.previewTransfer
        return when {
            transaction.coordination.activeOperation.isSwitching() -> {
                SelectionApplyResult.Rejected(WorkspaceActionRejection.PersistenceBusy)
            }

            session == null -> {
                rejected(SelectionRejection.NoSession)
            }

            session.geometry.pointer != null -> {
                rejected(SelectionRejection.PointerActive)
            }

            transfer == null -> {
                rejected(SelectionRejection.NoRectangle)
            }

            else -> {
                val command = TransferPixelsCommand.create(session.admission, session.layerId, transfer)
                when (val result = transaction.executeCommand(command)) {
                    is CommandResult.Applied -> SelectionApplyResult.Applied
                    is CommandResult.Failed -> SelectionApplyResult.CommandFailed(result.failure)
                    is CommandResult.Rejected -> rejectedCommand(transaction, result)
                }
            }
        }
    }

    private fun rejectedCommand(
        transaction: EditorRuntime.RuntimeTransaction,
        result: CommandResult.Rejected,
    ): SelectionApplyResult =
        when {
            result.reason == RejectionReason.NoEffectiveChange -> {
                transaction.reduceWorkspace(WorkspaceAction.CancelSelectionSession)
                SelectionApplyResult.NoChange
            }

            result.reason == RejectionReason.SourceOwnerMismatch ||
                result.reason == RejectionReason.SourceHistoryMismatch ||
                result.reason is RejectionReason.TargetDocumentMismatch -> {
                transaction.reduceWorkspace(WorkspaceAction.CancelSelectionSession)
                SelectionApplyResult.CommandRejected(result.reason)
            }

            else -> {
                SelectionApplyResult.CommandRejected(result.reason)
            }
        }

    private fun rejected(reason: SelectionRejection): SelectionApplyResult =
        SelectionApplyResult.Rejected(WorkspaceActionRejection.SelectionRejected(reason))
}
