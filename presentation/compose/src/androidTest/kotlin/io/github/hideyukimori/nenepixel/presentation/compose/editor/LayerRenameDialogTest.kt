package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.CANCEL_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.RENAME_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The layer rename dialog (#144 U7): opening, confirming, the refused names, an unchanged name and cancel. */
internal class LayerRenameDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renameFromTheMenuOpensTheDialogWithTheCurrentNameSelected() {
        val (_, id) = shownWithName(SKY)
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().assert(editableText(SKY))
        input().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, SKY.length)))
    }

    @Test
    fun anEmptyNameStartsEmptyAndTheHintSaysTheShownName() {
        val (_, id) = shownWithName("")
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().assert(editableText(""))
        val hint = text(R.string.layer_rename_hint, defaultName(id))
        composeRule.onNode(hasText(hint), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun confirmingANewNameRenamesTheRowAndTheChipAndUndoRestoresIt() {
        val (controller, id) = shownWithName("")
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().performTextReplacement(SEA)
        LayerMenuFixture.click(composeRule, CONFIRM_TAG)
        input().assertDoesNotExist()
        assertEquals(SEA, nameOf(controller))
        LayerMenuFixture.row(composeRule, id).assert(hasContentDescription(text(R.string.layer_row, SEA)))
        LayerMenuFixture.click(composeRule, CLOSE_TAG)
        composeRule.onNodeWithTag(CHIP_TAG).assert(hasText(SEA))
        LayerMenuFixture.undo(composeRule, controller)
        assertEquals("", nameOf(controller))
        composeRule.onNodeWithTag(CHIP_TAG).assert(hasText(defaultName(id)))
    }

    @Test
    fun confirmingAnEmptyNameShowsTheDefaultLabel() {
        val (controller, id) = shownWithName(SKY)
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().performTextReplacement("")
        LayerMenuFixture.click(composeRule, CONFIRM_TAG)
        input().assertDoesNotExist()
        assertEquals("", nameOf(controller))
        LayerMenuFixture.row(composeRule, id).assert(hasContentDescription(text(R.string.layer_row, defaultName(id))))
    }

    @Test
    fun aNameOverTheLimitShowsTheProblemKeepsTheDialogAndEditingClearsIt() {
        val (controller, id) = shownWithName(SKY)
        val limit = LayerLimits.MAX_NAME_CODE_POINTS
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().performTextReplacement("あ".repeat(limit + 1))
        LayerMenuFixture.click(composeRule, CONFIRM_TAG)
        val message = resources().getQuantityString(R.plurals.layer_rename_too_long, limit, limit)
        rejection().assertIsDisplayed().assert(hasText(message))
        input().assertIsDisplayed()
        assertEquals(SKY, nameOf(controller))
        input().performTextReplacement(SEA)
        composeRule.waitForIdle()
        rejection().assertDoesNotExist()
    }

    @Test
    fun aNameWithALineBreakShowsTheInvalidCharacterProblem() {
        val (controller, id) = shownWithName(SKY)
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().performTextReplacement("a\nb")
        LayerMenuFixture.click(composeRule, CONFIRM_TAG)
        rejection().assertIsDisplayed().assert(hasText(text(R.string.layer_rename_invalid)))
        input().assertIsDisplayed()
        assertEquals(SKY, nameOf(controller))
    }

    @Test
    fun confirmingTheSameNameClosesWithoutAddingHistory() {
        val (controller, id) = shownWithName("")
        assertFalse("A fresh one-layer editor has no history", controller.renderState.canUndo)
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        LayerMenuFixture.click(composeRule, CONFIRM_TAG)
        input().assertDoesNotExist()
        assertFalse("An unchanged name must not add history", controller.renderState.canUndo)
        assertEquals("", nameOf(controller))
    }

    @Test
    fun cancelClosesTheDialogAndKeepsTheName() {
        val (controller, id) = shownWithName(SKY)
        LayerMenuFixture.choose(composeRule, id, RENAME_TAG)
        input().performTextReplacement(SEA)
        LayerMenuFixture.click(composeRule, CANCEL_TAG)
        input().assertDoesNotExist()
        assertEquals(SKY, nameOf(controller))
    }

    /** A one-layer editor whose layer is named [name] (through the layer route when not empty), and its id. */
    private fun shownWithName(name: String): Pair<EditorController, LayerId> {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val id = controller.renderState.activeLayerId
        if (name.isNotEmpty()) {
            composeRule.runOnIdle { controller.callbacks.layers.onRename(id, layerName(name)) }
            composeRule.waitForIdle()
        }
        return controller to id
    }

    private fun nameOf(controller: EditorController): String =
        controller.renderState.document.layers
            .single()
            .name.value

    private fun input() = composeRule.onNodeWithTag(INPUT_TAG)

    private fun rejection() = composeRule.onNodeWithTag(REJECTION_TAG, useUnmergedTree = true)

    private fun editableText(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))

    private fun defaultName(id: LayerId): String = text(R.string.layer_default_name, id.value)

    private fun resources() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun layerName(value: String): LayerName =
        when (val result = LayerName.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid rename fixture name: ${result.rejection}")
        }

    private companion object {
        const val SKY: String = "Sky"
        const val SEA: String = "Sea"
        const val INPUT_TAG: String = "editor_layer_name_input"
        const val REJECTION_TAG: String = "editor_layer_name_rejection"
        const val CONFIRM_TAG: String = "editor_layer_rename_confirm"
        const val CHIP_TAG: String = "editor_layer_chip"
        const val CLOSE_TAG: String = "editor_layer_panel_close"
    }
}
