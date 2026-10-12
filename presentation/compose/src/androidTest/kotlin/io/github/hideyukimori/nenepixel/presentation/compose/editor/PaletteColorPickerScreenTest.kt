package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Issue #168: native input lifecycle, numeric synchronization and reachable footer. */
internal class PaletteColorPickerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneDragPreviewsTheFinalColorAndCreatesOneUndoRedoStep() {
        val controller = shownPicker()
        enterHex("#FF0000FF")
        composeRule.runOnIdle { controller.callbacks.palette.onUndo() }
        val original = draft(controller)
        scrollTo("editor_palette_picker_sv").performTouchInput {
            down(Offset(width * 0.2f, height * 0.2f))
            moveTo(Offset(width * 0.4f, height * 0.4f))
            moveTo(Offset(width * 0.8f, height * 0.3f))
            up()
        }
        val edited = draft(controller)
        assertNotEquals(original, edited)
        val expected =
            PaletteHsvColor
                .fromColor(
                    original.palette.entries()[1].color,
                ).copy(saturation = 0.8f, value = 0.7f)
        assertEquals(
            expected.toColor(
                original.palette
                    .entries()[1]
                    .color.alpha.value
                    .toInt(),
            ),
            selectedColor(controller),
        )
        assertFalse(requireNotNull(controller.renderState.paletteEditSession).isColorEditActive)
        scrollTo("editor_palette_editor_hex").assertTextContains(PaletteHexColor.format(selectedColor(controller)))
        composeRule.onNodeWithTag("editor_palette_editor_undo").performClick()
        assertEquals(original, draft(controller))
        assertFalse(requireNotNull(controller.renderState.paletteEditSession).canUndoDraft)
        composeRule.onNodeWithTag("editor_palette_editor_redo").performClick()
        assertEquals(edited, draft(controller))
    }

    @Test
    fun numericHexAndAlphaAccessibilityShareExactRgbAndPickerPosition() {
        val controller = shownPicker()
        enterHex("#13A6EF00")
        val color = selectedColor(controller)
        scrollTo("editor_palette_picker_opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(PaletteHsvColor.withAlpha(color, 0.5f), selectedColor(controller))
        scrollTo("editor_palette_editor_hex").assertTextContains("#13A6EF80")
        scrollTo("editor_palette_editor_numeric").performClick()
        val redInput = scrollTo("editor_palette_editor_r")
        redInput.performTextReplacement("255")
        redInput.performImeAction()
        val numeric = selectedColor(controller)
        assertEquals(255, numeric.red.value.toInt())
        val hue = PaletteHsvColor.fromColor(numeric).hue / PaletteHsvColor.FULL_HUE
        scrollTo("editor_palette_picker_hue").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(hue, 0f..1f)),
        )
        scrollTo("editor_palette_picker_hue").performSemanticsAction(SemanticsActions.SetProgress) { it(hue) }
        assertEquals(numeric, selectedColor(controller))
    }

    @Test
    fun cancelledPointerRestoresTheStartingDraftAndFooterStaysVisible() {
        val controller = shownPicker()
        val original = draft(controller)
        scrollTo("editor_palette_picker_sv").performTouchInput {
            down(center)
            moveTo(Offset(width * 0.1f, height * 0.1f))
            cancel()
        }
        assertEquals(original, draft(controller))
        assertFalse(requireNotNull(controller.renderState.paletteEditSession).isColorEditActive)
        assertFalse(requireNotNull(controller.renderState.paletteEditSession).canUndoDraft)
        scrollTo("editor_palette_picker_sv").performTouchInput {
            down(Offset(width * 0.8f, height * 0.3f))
            up()
        }
        assertFalse(requireNotNull(controller.renderState.paletteEditSession).isColorEditActive)
        assertTrue(requireNotNull(controller.renderState.paletteEditSession).canUndoDraft)
        composeRule.onNodeWithTag("editor_palette_editor_undo").performClick()
        assertEquals(original, draft(controller))
        scrollTo("editor_palette_editor_operations").performClick()
        scrollTo("editor_export_palette_json").assertIsDisplayed()
        FOOTER.forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }
        composeRule.onNodeWithTag("editor_palette_editor_cancel").performClick()
        assertEquals(original, controller.renderState.definition)
    }

    private fun shownPicker(): EditorController {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        scrollTo("editor_palette_editor_slot_2").performClick()
        assertTrue(requireNotNull(controller.renderState.paletteEditSession).pendingImport == null)
        return controller
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag).performScrollTo()

    private fun enterHex(text: String) {
        val hexInput = scrollTo("editor_palette_editor_hex")
        hexInput.performTextReplacement(text)
        hexInput.performImeAction()
    }

    private fun draft(controller: EditorController): PaletteDefinition =
        requireNotNull(controller.renderState.paletteEditSession).draft

    private fun selectedColor(controller: EditorController): PixelColor = draft(controller).palette.entries()[1].color

    private companion object {
        val FOOTER: List<String> =
            listOf(
                "editor_palette_editor_undo",
                "editor_palette_editor_redo",
                "editor_palette_editor_apply",
                "editor_palette_editor_cancel",
            )
    }
}
