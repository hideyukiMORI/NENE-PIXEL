package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

internal class FrameScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun blankAddAndInactiveRowActionsPreserveTargetsWithUndoRedo() {
        val controller = PresentationTestValues.fixture().controller
        val first = controller.renderState.activeFrameId
        FrameScreenFixture.show(rule, controller)
        openFrames()
        click("editor_frame_add")
        val second = controller.renderState.activeFrameId
        menu(first, "editor_frame_duplicate")
        val copy = controller.renderState.activeFrameId
        menu(copy, "editor_frame_later")
        assertEquals(listOf(first, second, copy), controller.documentState.frames.map { it.id })
        menu(second, "editor_frame_delete")
        val deleted = controller.documentState
        rule.runOnIdle { controller.callbacks.onUndo() }
        assertEquals(listOf(first, second, copy), controller.documentState.frames.map { it.id })
        rule.runOnIdle { controller.callbacks.onRedo() }
        assertEquals(deleted.frames, controller.documentState.frames)
        click("editor_frame_row_${first.value}")
        rule.onNodeWithTag("editor_layer_panel").assertIsDisplayed()
        assertEquals(first, controller.renderState.activeFrameId)
    }

    @Test
    fun durationRequiresValidConfirmationAndBackKeepsThePaneHierarchy() {
        val controller = PresentationTestValues.fixture().controller
        FrameScreenFixture.show(rule, controller)
        openFrames()
        val first = controller.renderState.activeFrameId
        val before = controller.documentState
        menu(first, "editor_frame_duration")
        rule.onNodeWithTag("editor_frame_duration_milliseconds").performTextReplacement("9")
        click("editor_frame_duration_confirm")
        assertSame(before, controller.documentState)
        rule.onNodeWithTag("editor_frame_duration_milliseconds").performTextReplacement("250")
        click("editor_frame_duration_confirm")
        assertEquals(
            250,
            controller.documentState.frames
                .single()
                .duration.value,
        )
        val confirmed = controller.documentState
        menu(first, "editor_frame_duration")
        click("editor_frame_duration_milliseconds")
        rule.onNodeWithTag("editor_frame_duration_milliseconds").performTextReplacement("500")
        pressBack()
        rule.onNodeWithTag("editor_frame_duration_milliseconds").assertIsDisplayed()
        assertSame(confirmed, controller.documentState)
        pressBack()
        rule.onNodeWithTag("editor_frame_duration_milliseconds").assertDoesNotExist()
        assertSame(confirmed, controller.documentState)
        assertBackPaneHierarchy()
    }

    @Test
    fun shortFramePaneKeepsTheGlobalCapacityFooterReachable() {
        val controller = PresentationTestValues.fixture().controller
        repeat(14) { controller.callbacks.layers.onAdd() }
        controller.callbacks.frames.onAdd(controller.renderState.activeFrameId)
        FrameScreenFixture.show(rule, controller, height = 320.dp)
        openFrames()
        rule.onNodeWithTag("editor_frame_add").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("editor_document_layer_limit").assertIsDisplayed()
        val current = controller.renderState.activeFrameId
        click("editor_frame_more_${current.value}")
        rule.onNodeWithTag("editor_frame_duplicate").assertIsNotEnabled()
        click("editor_frame_duration")
        click("editor_cancel")
        rule.onNodeWithTag("editor_frame_add").assertIsDisplayed()
    }

    private fun openFrames() {
        click("editor_layer_chip")
        click("editor_frame_chosen")
        rule.onNodeWithTag("editor_frame_panel").assertIsDisplayed()
    }

    private fun menu(
        id: FrameId,
        action: String,
    ) {
        click("editor_frame_more_${id.value}")
        click(action)
    }

    private fun click(tag: String) = FrameScreenFixture.click(rule, tag)

    private fun assertBackPaneHierarchy() {
        rule.onNodeWithTag("editor_frame_panel").assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag("editor_layer_panel").assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag("editor_layer_chip").assertIsDisplayed()
    }

    private fun pressBack() {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }
}
