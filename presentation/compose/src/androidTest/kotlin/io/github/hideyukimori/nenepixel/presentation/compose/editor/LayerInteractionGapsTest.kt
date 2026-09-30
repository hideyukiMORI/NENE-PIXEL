package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.ADD_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.MAX_LAYERS
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.RENAME_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.frontToBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The layer panel interactions the #144 review found untested (U9, review B-1, B-3 and G-5): Back while quick select
 * is in tap mode, undo taking away the layer of an open menu or rename dialog, the IME Done action, the eyedropper on
 * a hidden active layer, and the scroll to a row added out of view.
 */
internal class LayerInteractionGapsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backDispatcher: OnBackPressedDispatcher? = null

    @Test
    fun backClosesQuickSelectInTapModeBeforeThePanel() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1) { dispatcher -> backDispatcher = dispatcher }
        LayerMenuFixture.openPanel(composeRule)
        composeRule.onNodeWithTag(QUICK_SELECT_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertNotNull(controller.renderState.quickSelection.menu)
        pressBack()
        assertNull(controller.renderState.quickSelection.menu)
        composeRule.onNodeWithTag(FAN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CLOSE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CHIP_TAG).assertDoesNotExist()
        pressBack()
        composeRule.onNodeWithTag(CLOSE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed()
    }

    @Test
    fun undoTakingAwayTheLayerOfAnOpenMenuRemovesTheMenu() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val added = addAndOpenMenu(controller)
        composeRule.onNodeWithTag(RENAME_TAG).assertIsDisplayed()
        LayerMenuFixture.undo(composeRule, controller)
        assertEquals(1, frontToBack(controller).size)
        LayerMenuFixture.row(composeRule, added).assertDoesNotExist()
        composeRule.onNodeWithTag(RENAME_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CLOSE_TAG).assertIsDisplayed()
    }

    @Test
    fun undoTakingAwayTheLayerOfTheRenameDialogClosesIt() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        addAndOpenMenu(controller)
        LayerMenuFixture.click(composeRule, RENAME_TAG)
        input().assertIsDisplayed()
        LayerMenuFixture.undo(composeRule, controller)
        assertEquals(1, frontToBack(controller).size)
        input().assertDoesNotExist()
    }

    @Test
    fun theImeDoneActionRenamesAndClosesLikeRename() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val id = controller.renderState.activeLayerId
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().performTextReplacement(SEA)
        input().performImeAction()
        composeRule.waitForIdle()
        input().assertDoesNotExist()
        assertEquals(
            SEA,
            controller.renderState.document.layers
                .single()
                .name.value,
        )
    }

    @Test
    fun theEyedropperOnAHiddenActiveLayerOffersShow() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val active = controller.renderState.activeLayerId
        composeRule.runOnIdle { controller.callbacks.layers.onSetVisibility(active, LayerVisibility.Hidden) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QUICK_SELECT_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EYEDROPPER_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(EyedropperState.Armed, controller.renderState.quickSelection.eyedropper)
        val activeIndex = controller.renderState.activePaletteIndex
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(activeIndex, controller.renderState.activePaletteIndex)
        notice(R.string.layer_notice_hidden).assertIsDisplayed()
        notice(R.string.layer_notice_show).assertIsDisplayed()
    }

    @Test
    fun addingFromTheBackLayerWithTheListScrolledUpShowsTheNewRow() {
        val controller = LayerMenuFixture.show(composeRule, layers = MAX_LAYERS - 1)
        val layers = frontToBack(controller)
        val back = layers.last()
        composeRule.runOnIdle { controller.callbacks.layers.onSelect(back) }
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.row(composeRule, back).assertIsDisplayed()
        LayerMenuFixture.row(composeRule, layers.first()).performScrollTo()
        composeRule.waitForIdle()
        LayerMenuFixture.row(composeRule, back).assertIsNotDisplayed()
        LayerMenuFixture.click(composeRule, ADD_TAG)
        val added = controller.renderState.activeLayerId
        assertEquals(MAX_LAYERS, frontToBack(controller).size)
        assertNotEquals(back, added)
        LayerMenuFixture.row(composeRule, added).assertIsDisplayed()
    }

    /** Opens the panel, adds a layer there and opens the new layer's menu; returns the new layer. */
    private fun addAndOpenMenu(controller: EditorController): LayerId {
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.click(composeRule, ADD_TAG)
        val added = frontToBack(controller).first()
        assertEquals(added, controller.renderState.activeLayerId)
        LayerMenuFixture.openMenu(composeRule, added)
        return added
    }

    private fun pressBack() {
        composeRule.runOnIdle { requiredBackDispatcher().onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun requiredBackDispatcher(): OnBackPressedDispatcher =
        backDispatcher ?: error("The test activity provides no OnBackPressedDispatcher")

    private fun input() = composeRule.onNodeWithTag(INPUT_TAG)

    private fun notice(resource: Int): SemanticsNodeInteraction =
        composeRule.onNode(
            hasText(InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)) and
                hasAnyAncestor(hasTestTag(NOTICE_TAG)),
        )

    private companion object {
        const val SEA: String = "Sea"
        const val QUICK_SELECT_TAG: String = "editor_quick_select"
        const val FAN_TAG: String = "editor_quick_select_fan"
        const val CHIP_TAG: String = "editor_layer_chip"
        const val CLOSE_TAG: String = "editor_layer_panel_close"
        const val INPUT_TAG: String = "editor_layer_name_input"
        const val EYEDROPPER_TAG: String = "editor_quick_select_eyedropper"
        const val CANVAS_TAG: String = "editor_canvas_64_48"
        const val NOTICE_TAG: String = "editor_layer_notice"
    }
}
