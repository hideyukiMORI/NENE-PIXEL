package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Uses the self-targeted ComponentActivity and the actual FramePanel's sole playback adapter. */
internal class PlaybackLifecycleScreenTest {
    @get:Rule val rule = createAndroidComposeRule(ComponentActivity::class.java)
    private val visible = mutableStateOf(true)
    private val paneIdentity = mutableStateOf(Any())

    @Test
    fun replacementBeforeOnStopCannotSurviveDelayedRecompositionOrResume() {
        val controller = show()
        val before = controller.runtime.state
        start()
        val old = requireNotNull(controller.renderState.playback.token)
        rule.mainClock.autoAdvance = false
        rule.activityRule.scenario.onActivity {
            controller.callbacks.playback.onStop()
            controller.callbacks.playback.onStart()
            assertNotSame(old, controller.renderState.playback.token)
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertNull(controller.workspaceState.playback.view.token)
        rule.activityRule.scenario.onActivity { activity ->
            PlaybackUiActions(activity.lifecycle, controller.callbacks.playback).start()
        }
        assertNull(controller.workspaceState.playback.view.token)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertNull(controller.renderState.playback.token)
        assertSame(before.documentState, controller.documentState)
        assertSame(before.historyAvailability, controller.runtime.state.historyAvailability)
        org.junit.Assert.assertEquals(before.workspaceState.activeFrameId, controller.renderState.picture.frameId)
    }

    @Test
    fun oldPaneCleanupCannotStopReplacementButRemovingItsCurrentPaneStopsWithoutResume() {
        val controller = show()
        start()
        val old = requireNotNull(controller.renderState.playback.token)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            controller.callbacks.playback.onStop()
            controller.callbacks.playback.onStart()
            paneIdentity.value = Any()
        }
        val replacement = requireNotNull(controller.renderState.playback.token)
        assertNotSame(old, replacement)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertSame(replacement, controller.renderState.playback.token)
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        assertNull(controller.renderState.playback.token)
        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        assertNull(controller.renderState.playback.token)
    }

    private fun show(): EditorController {
        val controller = FrameScreenFixture.twoPaintedFrames().controller
        controller.documentState.frames.forEach { frame ->
            controller.callbacks.frames.onDuration(frame.id, FrameDuration.create(SLOW_MILLISECONDS).frameValue())
        }
        rule.setContent {
            val state by controller.renderStates.collectAsState()
            if (visible.value) {
                key(paneIdentity.value) {
                    FramePanel(
                        framePanelModel(state),
                        FramePanelActions(controller.callbacks.frames, {}, {}),
                        Modifier.requiredSize(360.dp, 320.dp),
                    )
                }
            }
        }
        rule.waitForIdle()
        return controller
    }

    private fun start() {
        rule.onNodeWithTag("editor_playback_play").performClick()
        rule.waitForIdle()
    }

    private companion object {
        const val SLOW_MILLISECONDS = 60_000
    }
}
