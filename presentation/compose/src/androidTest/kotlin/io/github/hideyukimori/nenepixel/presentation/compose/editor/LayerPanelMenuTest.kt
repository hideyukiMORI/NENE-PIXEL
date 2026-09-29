package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.CANCEL_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.DELETE_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.MOVE_DOWN_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.MOVE_UP_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.RENAME_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.frontToBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A layer row's "more" menu: move up, move down, delete, their disabled ends, undo and closing (#144 U5). */
internal class LayerPanelMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moveUpTakesAMiddleRowOneStepToTheFront() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val (front, middle, back) = frontToBack(controller)
        LayerMenuFixture.choose(composeRule, middle, MOVE_UP_TAG)
        assertEquals(listOf(middle, front, back), frontToBack(controller))
    }

    @Test
    fun moveDownTakesAMiddleRowOneStepToTheBack() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val (front, middle, back) = frontToBack(controller)
        LayerMenuFixture.choose(composeRule, middle, MOVE_DOWN_TAG)
        assertEquals(listOf(front, back, middle), frontToBack(controller))
    }

    @Test
    fun theFrontRowCannotMoveUpAndTheBackRowCannotMoveDown() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val layers = frontToBack(controller)
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.openMenu(composeRule, layers.first())
        item(MOVE_UP_TAG).assertIsNotEnabled()
        item(MOVE_DOWN_TAG).assertIsEnabled()
        LayerMenuFixture.click(composeRule, RENAME_TAG)
        LayerMenuFixture.click(composeRule, CANCEL_TAG)
        LayerMenuFixture.openMenu(composeRule, layers.last())
        item(MOVE_UP_TAG).assertIsEnabled()
        item(MOVE_DOWN_TAG).assertIsNotEnabled()
        LayerMenuFixture.click(composeRule, RENAME_TAG)
        LayerMenuFixture.click(composeRule, CANCEL_TAG)
        assertEquals(layers, frontToBack(controller))
    }

    @Test
    fun deletingTheActiveRowRemovesItAndSelectsTheLayerNowAtItsPosition() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val (front, middle, back) = frontToBack(controller)
        composeRule.runOnIdle { controller.callbacks.layers.onSelect(middle) }
        LayerMenuFixture.choose(composeRule, middle, DELETE_TAG)
        assertEquals(listOf(front, back), frontToBack(controller))
        assertEquals(front, controller.renderState.activeLayerId)
        LayerMenuFixture.row(composeRule, middle).assertDoesNotExist()
    }

    @Test
    fun aSingleLayerCannotBeDeletedOrMoved() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.openMenu(composeRule, frontToBack(controller).single())
        item(DELETE_TAG).assertIsNotEnabled()
        item(MOVE_UP_TAG).assertIsNotEnabled()
        item(MOVE_DOWN_TAG).assertIsNotEnabled()
        item(RENAME_TAG).assertIsEnabled()
    }

    @Test
    fun undoRestoresTheOrderAfterAMoveAndTheLayerAfterADelete() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val layers = frontToBack(controller)
        LayerMenuFixture.choose(composeRule, layers[1], MOVE_UP_TAG)
        LayerMenuFixture.undo(composeRule, controller)
        assertEquals(layers, frontToBack(controller))
        LayerMenuFixture.openMenu(composeRule, layers[1])
        LayerMenuFixture.click(composeRule, DELETE_TAG)
        assertEquals(layers - layers[1], frontToBack(controller))
        LayerMenuFixture.undo(composeRule, controller)
        assertEquals(layers, frontToBack(controller))
        LayerMenuFixture.row(composeRule, layers[1]).assertExists()
    }

    @Test
    fun choosingAnItemClosesTheMenu() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val middle = frontToBack(controller)[1]
        LayerMenuFixture.choose(composeRule, middle, MOVE_UP_TAG)
        item(RENAME_TAG).assertDoesNotExist()
        LayerMenuFixture.openMenu(composeRule, middle)
        LayerMenuFixture.click(composeRule, RENAME_TAG)
        item(MOVE_UP_TAG).assertDoesNotExist()
    }

    private fun item(tag: String) = composeRule.onNodeWithTag(tag)
}
