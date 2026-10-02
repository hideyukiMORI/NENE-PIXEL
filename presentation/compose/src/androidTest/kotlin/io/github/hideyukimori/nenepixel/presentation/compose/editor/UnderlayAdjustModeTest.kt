package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.BAR_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.CHIP_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.CLOSE_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.DONE_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.FIT_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.OPACITY_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.TIMEOUT_MILLIS
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.enterAdjust
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.exists
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.underlay
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayAdjustFixture.waitForBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The underlay adjust mode (#171, ADR 0032): entering it from the row's menu, leaving it with Done or Back, moving
 * and scaling the underlay with one and two fingers on the canvas, Fit to picture and the bar's opacity slider.
 */
internal class UnderlayAdjustModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backDispatcher: OnBackPressedDispatcher? = null

    @Test
    fun moveAndScaleShowsTheBarAndClosesThePanel() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        enterAdjust(composeRule)
        composeRule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
        composeRule.waitUntil(TIMEOUT_MILLIS) { !exists(composeRule, CLOSE_TAG) }
        composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed()
        assertEquals(UnderlayInteraction.Adjusting, underlay(controller).interaction)
    }

    @Test
    fun doneRemovesTheBarAndATapDrawsAgain() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        enterAdjust(composeRule)
        LayerMenuFixture.click(composeRule, DONE_TAG)
        waitForBar(composeRule, shown = false)
        assertEquals(UnderlayInteraction.Resting, underlay(controller).interaction)
        assertEquals(0L, controller.documentState.revision.value)
        canvas(composeRule).performTouchInput { click(center) }
        composeRule.waitUntil(TIMEOUT_MILLIS) { controller.documentState.revision.value == 1L }
        assertTrue(controller.renderState.canUndo)
    }

    @Test
    fun backRemovesTheBar() {
        val controller = UnderlayAdjustFixture.show(composeRule) { dispatcher -> backDispatcher = dispatcher }
        enterAdjust(composeRule)
        UnderlayAdjustFixture.pressBack(composeRule, backDispatcher)
        waitForBar(composeRule, shown = false)
        assertEquals(UnderlayInteraction.Resting, underlay(controller).interaction)
    }

    @Test
    fun oneFingerDragMovesTheUnderlayAndLeavesTheDocument() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        enterAdjust(composeRule)
        val before = underlay(controller).placement
        canvas(composeRule).performTouchInput {
            swipe(start = center, end = percentOffset(DRAG_END, DRAG_END), durationMillis = SWIPE_MILLIS)
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { underlay(controller).placement != before }
        val after = underlay(controller).placement
        assertTrue(after.left > before.left)
        assertTrue(after.top > before.top)
        assertEquals(before.scale, after.scale, 0.0)
        assertEquals(0L, controller.documentState.revision.value)
        assertFalse(controller.renderState.canUndo)
    }

    @Test
    fun twoFingerPinchScalesTheUnderlayAndLeavesTheViewport() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        enterAdjust(composeRule)
        val before = underlay(controller).placement
        val viewport = controller.renderState.viewport
        canvas(composeRule).performTouchInput {
            down(pointerId = 0, position = percentOffset(HALF - PINCH_START, HALF))
            down(pointerId = 1, position = percentOffset(HALF + PINCH_START, HALF))
            (1..PINCH_STEPS).forEach { step ->
                val spread = PINCH_START + step * PINCH_STEP
                moveTo(pointerId = 0, position = percentOffset(HALF - spread, HALF))
                moveTo(pointerId = 1, position = percentOffset(HALF + spread, HALF))
            }
            up(pointerId = 0)
            up(pointerId = 1)
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { underlay(controller).placement.scale > before.scale }
        assertEquals(viewport, controller.renderState.viewport)
        assertEquals(0L, controller.documentState.revision.value)
        assertEquals(UnderlayInteraction.Adjusting, underlay(controller).interaction)
    }

    @Test
    fun fitToPictureRestoresTheFittedPlacementAndKeepsTheBar() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        enterAdjust(composeRule)
        val moved = underlay(controller).withPlacement(MOVED_LEFT, MOVED_TOP, MOVED_SCALE)
        composeRule.runOnIdle { controller.callbacks.underlay.onSet(moved) }
        val fitted = UnderlayAdjustFixture.fittedPlacement(controller)
        assertNotEquals(fitted, underlay(controller).placement)
        LayerMenuFixture.click(composeRule, FIT_TAG)
        composeRule.waitUntil(TIMEOUT_MILLIS) { underlay(controller).placement == fitted }
        composeRule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
        assertEquals(UnderlayInteraction.Adjusting, underlay(controller).interaction)
    }

    @Test
    fun theBarSliderChangesTheAlpha() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        enterAdjust(composeRule)
        composeRule.onNodeWithTag(OPACITY_TAG).performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(SLIDER_ALPHA.toFloat())
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { underlay(controller).opacity.alpha == SLIDER_ALPHA }
        composeRule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun aHiddenUnderlayCannotBeMovedAndScaled() {
        val controller = UnderlayAdjustFixture.show(composeRule)
        val hidden = underlay(controller).toggledVisibility()
        composeRule.runOnIdle { controller.callbacks.underlay.onSet(hidden) }
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.click(composeRule, UnderlayAdjustFixture.MORE_TAG)
        composeRule.onNodeWithTag(UnderlayAdjustFixture.ADJUST_TAG).assertIsNotEnabled()
        assertFalse(exists(composeRule, BAR_TAG))
    }

    @Test
    fun backClosesAnOpenPanelFirstAndThenEndsTheMode() {
        val controller = UnderlayAdjustFixture.show(composeRule) { dispatcher -> backDispatcher = dispatcher }
        enterAdjust(composeRule)
        LayerMenuFixture.openPanel(composeRule)
        composeRule.onNodeWithTag(CLOSE_TAG).assertIsDisplayed()
        UnderlayAdjustFixture.pressBack(composeRule, backDispatcher)
        composeRule.waitUntil(TIMEOUT_MILLIS) { !exists(composeRule, CLOSE_TAG) }
        composeRule.onNodeWithTag(BAR_TAG).assertIsDisplayed()
        assertEquals(UnderlayInteraction.Adjusting, underlay(controller).interaction)
        UnderlayAdjustFixture.pressBack(composeRule, backDispatcher)
        waitForBar(composeRule, shown = false)
        assertEquals(UnderlayInteraction.Resting, underlay(controller).interaction)
    }

    private companion object {
        const val DRAG_END: Float = 0.6f
        const val SWIPE_MILLIS: Long = 300
        const val HALF: Float = 0.5f
        const val PINCH_START: Float = 0.05f
        const val PINCH_STEP: Float = 0.03f
        const val PINCH_STEPS: Int = 6
        const val SLIDER_ALPHA: Int = 200
        const val MOVED_LEFT: Double = 5.0
        const val MOVED_TOP: Double = 7.0
        const val MOVED_SCALE: Double = 3.0
    }
}
