package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddFrameCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackPulseResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorPlaybackCallbacksTest {
    @Test
    fun `same frame samples keep render identity and a boundary changes display without editing focus`() {
        val controller = twoFrames()
        val first = controller.renderState.activeFrameId
        val document = controller.documentState
        val playing = controller.callbacks.playback.onStart()
        val token = requireNotNull(playing.playback.token)
        controller.callbacks.playback.onSample(token, 0L)
        controller.callbacks.playback.onSample(token, HALF_INTERVAL)
        assertSame(playing, controller.renderState)
        assertSame(playing.playback, controller.renderState.playback)
        assertEquals(playing.picture, controller.renderState.picture)
        controller.callbacks.playback.onSample(token, INTERVAL)
        val displayed = controller.renderState
        assertEquals(document.frames.last().id, displayed.displayedFrameId)
        assertEquals(displayed.displayedFrameId, displayed.picture.frameId)
        assertEquals(first, displayed.activeFrameId)
        assertSame(document, displayed.document)
        assertEquals(playing.canUndo, displayed.canUndo)
        assertEquals(playing.dirtyState, displayed.dirtyState)
        val stopped = controller.callbacks.playback.onStop()
        assertEquals(first, stopped.picture.frameId)
        assertNull(stopped.playback.token)
    }

    @Test
    fun `stale and clock fault pulses still synchronize the latest canonical projection`() {
        val controller = twoFrames()
        val token =
            requireNotNull(
                controller.callbacks.playback
                    .onStart()
                    .playback.token,
            )
        controller.callbacks.playback.onSample(token, 0L)
        val fault = controller.callbacks.playback.onSample(token, -1L)
        assertInstanceOf(PlaybackPulseResult.Stopped::class.java, fault)
        assertNull(controller.renderState.playback.token)
        controller.runtime.execute(
            AddFrameCommand.create(
                controller.runtime.captureSource(),
                controller.workspaceState.activeFrameId,
            ),
        )
        val latest = controller.runtime.state
        val stale = controller.callbacks.playback.onSample(token, INTERVAL)
        assertSame(PlaybackPulseResult.Stale, stale)
        assertSame(latest.documentState, controller.renderState.document)
        assertEquals(latest.workspaceState.activeFrameId, controller.renderState.picture.frameId)
    }

    @Test
    fun `first down stops and is ignored while a fresh down can draw and an old transform is rejected`() {
        val controller = twoFrames()
        val surface = viewport(ViewportSurface.create(400, 400, 1.0))
        val point = viewport(ViewportSurfacePoint.create(350.0, 350.0))
        controller.callbacks.playback.onStart()
        assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, controller.pointerDown(surface, point))
        assertNull(controller.renderState.playback.token)
        assertNull(controller.renderState.preview)
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, controller.pointerDown(surface, point))
        controller.pointerCancel()
        controller.viewportStarted(surface)
        val previous = controller.workspaceState.viewport
        controller.callbacks.playback.onStart()
        val first = viewport(ViewportSurfacePoint.create(150.0, 150.0))
        val moved = viewport(ViewportSurfacePoint.create(100.0, 100.0))
        val gesture = ViewportGesture.create(first, point, moved, point)
        val transformed = controller.viewportTransformed(surface, gesture)
        assertInstanceOf(PointerInputAcknowledgement.Rejected::class.java, transformed)
        assertEquals(previous, controller.workspaceState.viewport)
        controller.callbacks.playback.onStop()
        assertInstanceOf(
            PointerInputAcknowledgement.Accepted::class.java,
            controller.viewportTransformed(surface, gesture),
        )
        assertNotEquals(previous, controller.workspaceState.viewport)
    }

    @Test
    fun `relative delay rounds up nanoseconds without an absolute deadline`() {
        assertEquals(1L, playbackDelayMillis(1L))
        assertEquals(1L, playbackDelayMillis(1_000_000L))
        assertEquals(2L, playbackDelayMillis(1_000_001L))
        assertEquals(60_000L, playbackDelayMillis(60_000_000_000L))
    }

    private fun twoFrames(): EditorController {
        val controller = PresentationTestValues.fixture().controller
        val first = controller.renderState.activeFrameId
        controller.callbacks.frames.onAdd(first)
        controller.callbacks.frames.onSelect(first)
        return controller
    }

    private fun <T> viewport(result: ViewportValueResult<T>): T =
        when (result) {
            is ViewportValueResult.Created -> result.value
            is ViewportValueResult.Rejected -> error("Invalid playback input fixture: ${result.rejection}")
        }

    private companion object {
        const val HALF_INTERVAL = 50_000_000L
        const val INTERVAL = 100_000_000L
    }
}
