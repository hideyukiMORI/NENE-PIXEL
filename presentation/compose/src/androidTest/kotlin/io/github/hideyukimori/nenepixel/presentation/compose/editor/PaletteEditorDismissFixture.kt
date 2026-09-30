package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull

/** Issue #165: shared steps for the palette editor dismissal tests (close button, scrim, and Back). */
internal object PaletteEditorDismissFixture {
    const val CLOSE_TAG: String = "editor_close_panel"
    const val OPEN_EDITOR_TAG: String = "editor_palette_editor_open"
    const val CANCEL_TAG: String = "editor_palette_editor_cancel"
    const val DIALOG_TAG: String = "editor_palette_editor_discard_dialog"
    private const val HEX_TAG: String = "editor_palette_editor_hex"
    private const val MOVE_DOWN_TAG: String = "editor_palette_editor_move_down"
    private const val EDITED_HEX: String = "#FF000080"

    fun shownController(composeRule: ComposeContentTestRule): EditorController {
        val controller = QuickSelectFixture.controller()
        QuickSelectFixture.show(composeRule, controller)
        return controller
    }

    fun openPaletteEditor(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag(OPEN_EDITOR_TAG).performClick()
        composeRule.onNodeWithTag(CANCEL_TAG).assertExists()
    }

    /** Sets slot 2 through the hex field, as the palette editor screen test does, and returns the changed draft. */
    fun changeDraft(
        composeRule: ComposeContentTestRule,
        controller: EditorController,
    ): PaletteDefinition {
        composeRule.onNodeWithTag("editor_palette_editor_slot_2").performScrollTo().performClick()
        composeRule.onNodeWithTag(HEX_TAG).performScrollTo().performTextReplacement(EDITED_HEX)
        composeRule.onNodeWithTag(HEX_TAG).performImeAction()
        composeRule.waitForIdle()
        val edited = requireNotNull(controller.renderState.paletteEditSession) { "No palette edit session" }.draft
        assertNotEquals(controller.renderState.definition, edited)
        return edited
    }

    /**
     * Moves slot 2 down with buttons only, so no text field takes focus and no soft keyboard is left open to
     * swallow the next Back, and returns the changed draft.
     */
    fun changeDraftWithoutKeyboard(
        composeRule: ComposeContentTestRule,
        controller: EditorController,
    ): PaletteDefinition {
        composeRule.onNodeWithTag("editor_palette_editor_slot_2").performScrollTo().performClick()
        composeRule.onNodeWithTag(MOVE_DOWN_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        val edited = requireNotNull(controller.renderState.paletteEditSession) { "No palette edit session" }.draft
        assertNotEquals(controller.renderState.definition, edited)
        return edited
    }

    fun assertClosedWithoutConfirmation(
        composeRule: ComposeContentTestRule,
        controller: EditorController,
    ) {
        composeRule.onNodeWithTag(DIALOG_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CLOSE_TAG).assertDoesNotExist()
        assertNull(controller.renderState.paletteEditSession)
    }
}
