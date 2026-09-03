package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public data class EditorRuntimeState internal constructor(
    public val documentState: DocumentState,
    public val historyAvailability: HistoryAvailability,
    public val workspaceState: WorkspaceState,
    public val dirtyState: DocumentDirtyState,
)
