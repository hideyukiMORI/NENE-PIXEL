package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionInteraction
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.BAR_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.CANVAS_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.SELECT_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.pixel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Issue #211: real pointer lifetime, native bitmap, transient controls and existing overlay arbitration. */
internal class SelectionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun releasedMoveAndCopyPreviewMatchAppliedPixelsAndOneUndoRedo() {
        val controller = SelectionScreenFixture.show(rule)
        paint(controller, pixel(2, 1), 0)
        paint(controller, pixel(5, 1), 1)
        val before = controller.documentState
        begin(controller)
        drag(controller, pixel(2, 1), pixel(3, 1))
        drag(controller, pixel(2, 1), pixel(4, 1))

        assertSame(before, controller.documentState)
        assertEquals(SelectionInteraction.Idle, controller.renderState.selectionSession?.interaction)
        rule.onNodeWithTag(APPLY_TAG).assertIsEnabled()
        val moved = SelectionScreenFixture.samples(rule, controller)
        rule.onNodeWithTag(COPY_TAG).performClick()
        assertEquals(PixelTransferMode.Copy, controller.renderState.selectionSession?.mode)
        val copied = SelectionScreenFixture.samples(rule, controller)
        assertNotEquals(moved.first(), copied.first())
        assertEquals(moved.drop(1), copied.drop(1))
        rule.onNodeWithTag(APPLY_TAG).performClick()

        val applied = controller.documentState
        assertEquals(before.revision.value + 1L, applied.revision.value)
        assertNull(controller.renderState.selectionSession)
        assertEquals(copied, SelectionScreenFixture.samples(rule, controller))
        rule.onNodeWithTag("editor_undo").performClick()
        assertEquals(
            before.frames.single().layers,
            controller.documentState.frames
                .single()
                .layers,
        )
        rule.onNodeWithTag("editor_redo").performClick()
        assertEquals(
            applied.frames.single().layers,
            controller.documentState.frames
                .single()
                .layers,
        )
    }

    @Test
    fun systemCancelRestoresReleasedDraftAndFreshDragWorksWhileBarDoesNotLeak() {
        val controller = SelectionScreenFixture.show(rule)
        val before = controller.documentState
        begin(controller)
        drag(controller, pixel(2, 1), pixel(3, 1))
        val previous = controller.renderState.selectionSession?.previewTransfer
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(offset(controller, pixel(2, 1)))
            moveTo(offset(controller, pixel(3, 2)))
            cancel()
        }
        rule.waitForIdle()
        assertEquals(previous, controller.renderState.selectionSession?.previewTransfer)
        assertEquals(SelectionInteraction.Idle, controller.renderState.selectionSession?.interaction)

        drag(controller, pixel(2, 1), pixel(4, 1))
        assertEquals(
            pixel(4, 1),
            controller.renderState.selectionSession
                ?.targetRegion
                ?.origin,
        )
        val moved = controller.renderState.selectionSession?.previewTransfer
        rule.onNodeWithTag(BAR_TAG).performTouchInput {
            swipe(percentOffset(BAR_START, BAR_ROW), percentOffset(BAR_END, BAR_ROW), SWIPE_MILLIS)
        }
        rule.waitForIdle()
        assertEquals(moved, controller.renderState.selectionSession?.previewTransfer)
        assertSame(before, controller.documentState)
        rule.onNodeWithTag(CANCEL_TAG).performClick()
        assertNull(controller.renderState.selectionSession)
        assertSame(before, controller.documentState)
    }

    @Test
    fun moreTargetsVisibleInactiveRowAndBackClosesPanelBeforeSelection() {
        val controller = LayerMenuFixture.show(rule, 2)
        val target =
            controller.documentState.frames
                .single()
                .layers
                .first()
                .id
        val hidden =
            controller.documentState.frames
                .single()
                .layers
                .last()
                .id
        rule.runOnIdle { controller.callbacks.layers.onSetVisibility(hidden, LayerVisibility.Hidden) }
        LayerMenuFixture.openPanel(rule)
        LayerMenuFixture.openMenu(rule, hidden)
        rule.onNodeWithTag(SELECT_TAG).assertIsNotEnabled()
        pressBack()
        rule.onNodeWithTag(CLOSE_TAG).assertIsDisplayed()
        LayerMenuFixture.openMenu(rule, target)
        LayerMenuFixture.click(rule, SELECT_TAG)

        assertEquals(target, controller.renderState.activeLayerId)
        rule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
        rule.onNodeWithTag(CLOSE_TAG).assertDoesNotExist()
        val before = controller.documentState
        LayerMenuFixture.openPanel(rule)
        pressBack()
        rule.onNodeWithTag(CLOSE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag(BAR_TAG).assertDoesNotExist()
        assertNull(controller.renderState.selectionSession)
        assertSame(before, controller.documentState)
    }

    @Test
    fun twoPointersCancelOnlyCurrentDragAndRemainingFingerDoesNotResumeTranslation() {
        val controller = SelectionScreenFixture.show(rule)
        val before = controller.documentState
        begin(controller)
        drag(controller, pixel(2, 1), pixel(3, 1))
        val previous = controller.renderState.selectionSession?.previewTransfer
        val viewport = controller.renderState.viewport
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            val first = offset(controller, pixel(3, 1))
            val second = offset(controller, pixel(5, 1))
            down(pointerId = 0, position = offset(controller, pixel(2, 1)))
            moveTo(pointerId = 0, position = first)
            down(pointerId = 1, position = second)
            moveTo(pointerId = 0, position = first + Offset(-PINCH_PIXELS, 0f))
            moveTo(pointerId = 1, position = second + Offset(PINCH_PIXELS, 0f))
            up(pointerId = 1)
            moveTo(pointerId = 0, position = first)
            up(pointerId = 0)
        }
        rule.waitForIdle()

        assertNotEquals(viewport, controller.renderState.viewport)
        assertEquals(previous, controller.renderState.selectionSession?.previewTransfer)
        assertEquals(SelectionInteraction.Idle, controller.renderState.selectionSession?.interaction)
        assertSame(before, controller.documentState)
    }

    /** The focused popup must receive Back before the activity's handlers (same boundary as palette dialogs). */
    private fun pressBack() {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun begin(controller: EditorController) {
        LayerMenuFixture.choose(
            rule,
            controller.documentState.frames
                .single()
                .layers
                .single()
                .id,
            SELECT_TAG,
        )
        rule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
        rule.onNodeWithTag(APPLY_TAG).assertIsNotEnabled()
    }

    private fun paint(
        controller: EditorController,
        pixel: PixelPosition,
        slot: Int,
    ) {
        rule.runOnIdle { controller.callbacks.onSelectPaletteEntry(QuickSelectFixture.index(slot)) }
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(offset(controller, pixel)) }
        rule.waitForIdle()
    }

    private fun drag(
        controller: EditorController,
        from: PixelPosition,
        to: PixelPosition,
    ) {
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(offset(controller, from))
            moveTo(offset(controller, to))
            up()
        }
        rule.waitForIdle()
        assertTrue(controller.renderState.selectionSession != null)
    }

    private fun TouchInjectionScope.offset(
        controller: EditorController,
        pixel: PixelPosition,
    ): Offset = SelectionScreenFixture.offset(this, rule, controller, pixel)

    private companion object {
        const val APPLY_TAG = "editor_selection_apply"
        const val COPY_TAG = "editor_selection_copy"
        const val CANCEL_TAG = "editor_selection_cancel"
        const val CLOSE_TAG = "editor_layer_panel_close"
        const val SWIPE_MILLIS = 300L
        const val BAR_START = 0.1f
        const val BAR_END = 0.8f
        const val BAR_ROW = 0.05f
        const val PINCH_PIXELS = 24f
    }
}
