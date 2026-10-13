package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection

/** Workspace-only drafts and temporary pointer modes retain their existing cancellation paths. */
internal data class WorkspaceSessions(
    val paletteEditSession: PaletteEditSession?,
    val pendingImport: PendingRasterImport?,
    val quickSelection: QuickSelection,
    val previewSessions: WorkspacePreviewSessions,
) {
    /** Copies only an actual preview change; view-only transitions share these existing sessions. */
    fun withPreviews(previews: WorkspacePreviewSessions): WorkspaceSessions =
        if (previews.selection === previewSessions.selection && previews.playback === previewSessions.playback) {
            this
        } else {
            copy(previewSessions = previews)
        }
}
