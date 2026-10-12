package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackState
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionSession

/** Immutable selection geometry/admission and playback timing, with no mutation or cancellation in construction. */
internal data class WorkspacePreviewSessions(
    val selection: SelectionSession?,
    val playback: PlaybackState,
)

internal fun WorkspaceState.withPlayback(playback: PlaybackState): WorkspaceState =
    WorkspaceState(
        editTarget,
        activeTool,
        viewport,
        preview,
        appearance,
        actualSizeWindow,
        paletteEditSession,
        quickSelection,
        underlay,
        pendingImport,
        previewSessions.copy(playback = playback),
    )
