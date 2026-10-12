package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackMode
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackPulseResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackToken

/** The sole presentation playback capability; every pulse also projects the latest canonical owners. */
internal class EditorPlaybackCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    val isPlaying: Boolean get() = runtime.state.workspaceState.playback.view.token != null

    fun onStart(): EditorRenderState = reduce(WorkspaceAction.StartPlayback)

    fun onStop(): EditorRenderState = reduce(WorkspaceAction.StopPlayback)

    fun onStopIfCurrent(token: PlaybackToken): EditorRenderState = reduce(WorkspaceAction.StopPlaybackIfCurrent(token))

    fun onMode(mode: PlaybackMode): EditorRenderState = reduce(WorkspaceAction.SetPlaybackMode(mode))

    fun onSample(
        token: PlaybackToken,
        nanos: Long,
    ): PlaybackPulseResult {
        val result = runtime.advancePlayback(token, nanos)
        // Even Stale/fault samples may follow an asynchronous install/checkpoint change.
        publish(adapter.renderState)
        return result
    }

    private fun reduce(action: WorkspaceAction.PlaybackAction): EditorRenderState {
        val result = runtime.reduce(action)
        adapter.frameNotice =
            if (result is WorkspaceReductionResult.Rejected) FrameEditorNotice.Rejected(result.rejection) else null
        return publish(adapter.renderState)
    }
}
