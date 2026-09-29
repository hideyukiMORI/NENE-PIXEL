package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.DELETE_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.frontToBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The layer notice: deletion with undo, drawing on a hidden layer with show, and repeated refusals (#144 U6). */
internal class LayerNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deletingShowsTheNoticeAndUndoBringsTheLayerBack() {
        val controller = LayerMenuFixture.show(composeRule, layers = 2)
        val before = frontToBack(controller)
        LayerMenuFixture.choose(composeRule, before.first(), DELETE_TAG)
        notice(R.string.layer_notice_deleted).assertIsDisplayed()
        notice(R.string.layer_notice_undo).performClick()
        composeRule.waitForIdle()
        assertEquals(before, frontToBack(controller))
        notice(R.string.layer_notice_deleted).assertDoesNotExist()
    }

    @Test
    fun anotherEditAfterTheDeletionRemovesTheNotice() {
        val controller = LayerMenuFixture.show(composeRule, layers = 2)
        val (front, back) = frontToBack(controller)
        LayerMenuFixture.choose(composeRule, front, DELETE_TAG)
        notice(R.string.layer_notice_deleted).assertIsDisplayed()
        composeRule.runOnIdle { controller.callbacks.layers.onSetVisibility(back, LayerVisibility.Hidden) }
        composeRule.waitForIdle()
        assertNull(controller.renderState.layerNotice)
        notice(R.string.layer_notice_deleted).assertDoesNotExist()
    }

    @Test
    fun drawingOnAHiddenLayerOffersShowAndTheNextStrokeDraws() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val active = controller.renderState.activeLayerId
        composeRule.runOnIdle { controller.callbacks.layers.onSetVisibility(active, LayerVisibility.Hidden) }
        val hiddenRevision = controller.renderState.document.revision
        tapCanvas()
        assertEquals(hiddenRevision, controller.renderState.document.revision)
        notice(R.string.layer_notice_hidden).assertIsDisplayed()
        notice(R.string.layer_notice_show).performClick()
        composeRule.waitForIdle()
        val shown = controller.renderState.document
        assertEquals(LayerVisibility.Visible, shown.layers.single().visibility)
        tapCanvas()
        assertNotEquals(shown.revision, controller.renderState.document.revision)
    }

    @Test
    fun theSameRefusalTwiceShowsTheNoticeAgain() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val active = controller.renderState.activeLayerId
        composeRule.runOnIdle { controller.callbacks.layers.onSetVisibility(active, LayerVisibility.Hidden) }
        tapCanvas()
        val first = controller.renderState.layerNotice?.serial
        notice(R.string.layer_notice_hidden).assertIsDisplayed()
        tapCanvas()
        val second = controller.renderState.layerNotice?.serial
        assertNotEquals(first, second)
        notice(R.string.layer_notice_hidden).assertIsDisplayed()
    }

    private fun tapCanvas() {
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun notice(resource: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text(resource)) and inNotice)

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private companion object {
        const val CANVAS_TAG: String = "editor_canvas_64_48"
        val inNotice: SemanticsMatcher = hasAnyAncestor(hasTestTag("editor_layer_notice"))
    }
}
