package io.github.hideyukimori.nenepixel.presentation.compose.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

internal class PlaybackBitmapProjectionTest {
    @Test
    fun sameFrameSamplesReuseBitmapAndAnObservedBoundaryUsesOnlyTheDisplayedFrame() {
        val controller = FrameScreenFixture.twoPaintedFrames().controller
        val first =
            controller.documentState.frames
                .first()
                .id
        controller.callbacks.frames.onSelect(first)
        val token =
            requireNotNull(
                controller.callbacks.playback
                    .onStart()
                    .playback.token,
            )
        val cache = CommittedBitmapCache()
        val red = cache.render(controller.renderState.picture)
        controller.callbacks.playback.onSample(token, 0L)
        controller.callbacks.playback.onSample(token, HALF_INTERVAL)
        assertSame(red, cache.render(controller.renderState.picture))
        controller.callbacks.playback.onSample(token, INTERVAL)
        val green = cache.render(controller.renderState.picture)
        assertNotSame(red, green)
        assertEquals(GREEN, green.getPixel(0, 0))
        assertEquals(first, controller.renderState.activeFrameId)
        controller.callbacks.playback.onStop()
        assertEquals(RED, cache.render(controller.renderState.picture).getPixel(0, 0))
    }

    private companion object {
        const val HALF_INTERVAL = 50_000_000L
        const val INTERVAL = 100_000_000L
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
    }
}
