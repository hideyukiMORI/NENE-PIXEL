package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

internal class PlaybackScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun framesPreviewSharesCanvasAndWindowThenConsumesTheWholeFirstGestureAndBackStopsInPlace() {
        val controller = PlaybackScreenFixture.controller()
        controller.callbacks.onSetActualSizeWindow(
            controller.renderState.actualSizeWindow
                .toggled()
                .withScale(ActualSizeScale.X32),
        )
        val before = controller.documentState
        FrameScreenFixture.show(rule, controller)
        openFrames()
        click("editor_playback_play")
        rule.waitUntil { controller.renderState.displayedFrameId != controller.renderState.activeFrameId }
        assertEquals(GREEN, PlaybackScreenFixture.windowPixel(rule))
        assertEquals(GREEN, PlaybackScreenFixture.canvasPixel(rule, controller))
        assertSame(before, controller.documentState)
        assertWholeFirstGestureIsConsumed(controller)
        assertEquals(RED, PlaybackScreenFixture.windowPixel(rule))
        freshGesture(controller)
        assertEquals(before.revision.value + 1L, controller.documentState.revision.value)
        click("editor_playback_play")
        PlaybackScreenFixture.back()
        rule.waitForIdle()
        assertNull(controller.renderState.playback.token)
        rule.onNodeWithTag("editor_frame_panel").assertIsDisplayed()
        PlaybackScreenFixture.back()
        rule.waitForIdle()
        rule.onNodeWithTag("editor_layer_panel").assertIsDisplayed()
    }

    @Test
    fun shortPaneKeepsStopFixedWhileModeOptionsScrollAndOneFrameExplainsTheDisabledStart() {
        val controller = PlaybackScreenFixture.controller()
        repeat(14) { controller.callbacks.layers.onAdd() }
        FrameScreenFixture.show(rule, controller, height = 320.dp)
        openFrames()
        click("editor_playback_play")
        rule.onNodeWithTag("editor_playback_stop").assertIsDisplayed()
        rule.onNodeWithTag("editor_document_layer_limit").assertIsDisplayed()
        rule.onNodeWithTag("editor_playback_once").performScrollTo()
        click("editor_playback_once")
        assertNull(controller.renderState.playback.token)
        assertEquals(PlaybackMode.Once, controller.renderState.playback.mode)
        click("editor_playback_play")
        click("editor_playback_stop")
        rule.onNodeWithTag("editor_playback_play").assertIsDisplayed()
        val extra =
            controller.documentState.frames
                .last()
                .id
        rule.runOnIdle { controller.callbacks.frames.onDelete(extra) }
        rule.onNodeWithTag("editor_playback_play").assertIsNotEnabled()
        rule.onNodeWithTag("editor_playback_requires_frames").assertExists()
    }

    private fun assertWholeFirstGestureIsConsumed(controller: EditorController) {
        val before = controller.documentState
        val viewport = controller.renderState.viewport
        rule.onNodeWithTag(PlaybackScreenFixture.CANVAS_TAG).performTouchInput {
            val start = PlaybackScreenFixture.offset(this, rule, controller, FrameTestValues.position(3, 3))
            val moved = PlaybackScreenFixture.offset(this, rule, controller, FrameTestValues.position(2, 3))
            down(0, start)
            down(1, moved)
            moveTo(0, moved)
            up(0)
            up(1)
        }
        rule.waitForIdle()
        assertNull(controller.renderState.playback.token)
        assertSame(before, controller.documentState)
        assertEquals(viewport, controller.renderState.viewport)
    }

    private fun freshGesture(controller: EditorController) {
        rule.onNodeWithTag(PlaybackScreenFixture.CANVAS_TAG).performTouchInput {
            down(PlaybackScreenFixture.offset(this, rule, controller, FrameTestValues.position(3, 2)))
            up()
        }
        rule.waitForIdle()
    }

    private fun openFrames() {
        click("editor_layer_chip")
        click("editor_frame_chosen")
    }

    private fun click(tag: String) = FrameScreenFixture.click(rule, tag)

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
    }
}
