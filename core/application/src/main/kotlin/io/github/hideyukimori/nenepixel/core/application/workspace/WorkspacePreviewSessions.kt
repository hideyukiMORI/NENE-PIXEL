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
        editing,
        preview,
        view,
        sessions.withPreviews(previewSessions.copy(playback = playback)),
    )
