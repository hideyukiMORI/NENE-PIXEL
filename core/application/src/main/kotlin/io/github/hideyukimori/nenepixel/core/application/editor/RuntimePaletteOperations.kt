package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.BeginPaletteEdit
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult

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
}
