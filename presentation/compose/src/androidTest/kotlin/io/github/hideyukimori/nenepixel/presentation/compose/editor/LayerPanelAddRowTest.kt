package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.ADD_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.LIMIT_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.MAX_LAYERS
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.frontToBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The layer panel's add row: adding selects the new layer, undo removes it, and the 16-layer limit (#144 U5). */
internal class LayerPanelAddRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addingPutsANewFrontRowAboveTheActiveLayerAndSelectsIt() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val before = frontToBack(controller)
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.click(composeRule, ADD_TAG)
        val after = frontToBack(controller)
        assertEquals(before, after.drop(1))
        assertEquals(after.first(), controller.renderState.activeLayerId)
        val current = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, currentText())
        LayerMenuFixture.row(composeRule, after.first()).assertIsDisplayed().assert(current)
    }

    @Test
    fun undoRemovesTheAddedRow() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val before = frontToBack(controller)
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.click(composeRule, ADD_TAG)
        val added = frontToBack(controller).first()
        LayerMenuFixture.undo(composeRule, controller)
        assertEquals(before, frontToBack(controller))
        LayerMenuFixture.row(composeRule, added).assertDoesNotExist()
    }

    @Test
    fun belowTheLimitAddIsEnabledAndStatesNoLimit() {
        LayerMenuFixture.show(composeRule, layers = MAX_LAYERS - 1)
        LayerMenuFixture.openPanel(composeRule)
        composeRule.onNodeWithTag(ADD_TAG).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag(LIMIT_TAG).assertDoesNotExist()
    }

    @Test
    fun sixteenLayersDisableAddAndStateTheLimit() {
        val controller = LayerMenuFixture.show(composeRule, layers = MAX_LAYERS)
        LayerMenuFixture.openPanel(composeRule)
        composeRule.onNodeWithTag(ADD_TAG).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag(LIMIT_TAG).assertIsDisplayed().assertTextEquals(limitText())
        assertEquals(MAX_LAYERS, frontToBack(controller).size)
    }

    private fun currentText(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.layer_row_state_current)

    private fun limitText(): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources
            .getQuantityString(R.plurals.layer_limit, MAX_LAYERS, MAX_LAYERS)
}
