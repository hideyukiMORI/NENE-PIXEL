package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame

internal object PlaybackTestValues {
    const val MS: Long = 1_000_000L

    fun document(): DocumentState =
        FrameTestValues.state(
            FrameTestValues.frame(4, listOf(layer(1, "first")), FrameTestValues.duration(10)),
            FrameTestValues.frame(2, listOf(layer(3, "middle")), FrameTestValues.duration(30)),
            FrameTestValues.frame(8, listOf(layer(5, "last")), FrameTestValues.duration(20)),
        )

    fun runtime(): EditorRuntime = FrameTestValues.runtime(document())

    fun start(
        runtime: EditorRuntime,
        mode: PlaybackMode = PlaybackMode.Loop,
    ): PlaybackToken {
        runtime.reduce(WorkspaceAction.SetPlaybackMode(mode))
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.reduce(WorkspaceAction.StartPlayback))
        return requireNotNull(runtime.state.workspaceState.playback.view.token)
    }

    fun pulse(
        runtime: EditorRuntime,
        token: PlaybackToken,
        nanos: Long,
    ): PlaybackPulseResult.Continue {
        val result = assertInstanceOf(PlaybackPulseResult.Continue::class.java, runtime.advancePlayback(token, nanos))
        assertSame(token, result.token)
        return result
    }
}
