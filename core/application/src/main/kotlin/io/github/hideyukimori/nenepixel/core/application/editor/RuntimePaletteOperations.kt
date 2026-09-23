package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.BeginPaletteEdit
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession

/** The runtime entry points of the palette draft session (ADR 0022); each runs as one runtime transaction. */
public class RuntimePaletteOperations internal constructor(
    private val runtime: EditorRuntime,
) {
    /** Opens a palette draft from the runtime's own source token and document definition. */
    public fun beginPaletteEdit(): WorkspaceReductionResult =
        runtime.transact { transaction ->
            val result =
                if (transaction.coordination.activeOperation.isSwitching()) {
                    WorkspaceReductionResult.Rejected(
                        transaction.workspaceState(),
                        WorkspaceActionRejection.PersistenceBusy,
                    )
                } else {
                    transaction.reduceWorkspace(
                        BeginPaletteEdit(
                            transaction.switchContext().source,
                            transaction.documentState().definition,
                        ),
                    )
                }
            PersistenceTransition(transaction.coordination, result)
        }

    /**
     * Applies the open palette draft as one `ReplacePaletteCommand` and closes the session.
     * Any refusal or command failure leaves the session and preview unchanged.
     */
    public fun applyPaletteDraft(): PaletteApplyResult =
        runtime.transact { transaction ->
            val session = transaction.workspaceState().paletteEditSession
            val result =
                when {
                    transaction.coordination.activeOperation.isSwitching() -> {
                        PaletteApplyResult.Rejected(WorkspaceActionRejection.PersistenceBusy)
                    }

                    session == null -> {
                        PaletteApplyResult.Rejected(WorkspaceActionRejection.NoPaletteSession)
                    }

                    session.base != transaction.switchContext().source -> {
                        PaletteApplyResult.Rejected(
                            WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.StaleBase),
                        )
                    }

                    else -> {
                        applySession(transaction, session)
                    }
                }
            PersistenceTransition(transaction.coordination, result)
        }

    private fun applySession(
        transaction: EditorRuntime.RuntimeTransaction,
        session: PaletteEditSession,
    ): PaletteApplyResult {
        if (session.isIdentity()) {
            transaction.reduceWorkspace(WorkspaceAction.CancelPaletteEdit)
            return PaletteApplyResult.NoChange
        }
        val command = ReplacePaletteCommand.create(transaction.captureSource(), session.composedRemap())
        return when (val commandResult = transaction.executeCommand(command)) {
            is CommandResult.Rejected -> {
                PaletteApplyResult.CommandRejected(commandResult.reason)
            }

            is CommandResult.Failed -> {
                PaletteApplyResult.CommandFailed(commandResult.failure)
            }

            is CommandResult.Applied -> {
                closeAppliedSession(transaction)
                PaletteApplyResult.Applied
            }
        }
    }

    private fun closeAppliedSession(transaction: EditorRuntime.RuntimeTransaction) {
        if (transaction.workspaceState().preview != null) {
            transaction.reduceWorkspace(WorkspaceAction.CancelGesturePreview)
        }
        transaction.reduceWorkspace(WorkspaceAction.CancelPaletteEdit)
    }
}
