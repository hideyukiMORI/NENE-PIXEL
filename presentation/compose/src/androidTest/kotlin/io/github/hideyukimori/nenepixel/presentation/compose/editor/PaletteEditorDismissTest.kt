package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PaletteEditorDismissFixture.CANCEL_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PaletteEditorDismissFixture.CLOSE_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PaletteEditorDismissFixture.DIALOG_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PaletteEditorDismissFixture.OPEN_EDITOR_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Issue #165 S2: the palette editor's close button and scrim end an unchanged draft at once and ask before
 * discarding a changed one. Back (rulings 3 and 6) is covered by [PaletteEditorDismissBackTest].
 */
internal class PaletteEditorDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unchangedDraftCloseButtonEndsTheSessionWithoutConfirmation() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        PaletteEditorDismissFixture.assertClosedWithoutConfirmation(composeRule, controller)
    }

    @Test
    fun unchangedDraftScrimTapEndsTheSessionWithoutConfirmation() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        tapScrimOutsidePanel()
        PaletteEditorDismissFixture.assertClosedWithoutConfirmation(composeRule, controller)
    }

    @Test
    fun changedDraftCloseButtonThenDiscardLeavesTheDocumentPalette() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        val before = controller.renderState.definition
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        PaletteEditorDismissFixture.changeDraft(composeRule, controller)
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        composeRule.onNodeWithTag(DIALOG_TAG).assertExists()
        composeRule.onNodeWithTag(DISCARD_TAG).performClick()
        PaletteEditorDismissFixture.assertClosedWithoutConfirmation(composeRule, controller)
        assertEquals(before, controller.renderState.definition)
    }

    @Test
    fun changedDraftCloseButtonThenKeepEditingKeepsTheDraftAndThePanel() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        val before = controller.renderState.definition
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        val edited = PaletteEditorDismissFixture.changeDraft(composeRule, controller)
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        composeRule.onNodeWithTag(DIALOG_TAG).assertExists()
        composeRule.onNodeWithTag(KEEP_TAG).performClick()
        composeRule.onNodeWithTag(DIALOG_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CANCEL_TAG).assertExists()
        assertEquals(edited, controller.renderState.paletteEditSession?.draft)
        assertNotEquals(before, edited)
        assertEquals(before, controller.renderState.definition)
    }

    @Test
    fun changedDraftScrimTapAsksForConfirmation() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        val edited = PaletteEditorDismissFixture.changeDraft(composeRule, controller)
        tapScrimOutsidePanel()
        composeRule.onNodeWithTag(DIALOG_TAG).assertExists()
        assertEquals(edited, controller.renderState.paletteEditSession?.draft)
    }

    @Test
    fun palettePanelCloseButtonStillClosesWithoutConfirmation() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag(OPEN_EDITOR_TAG).assertExists()
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        composeRule.onNodeWithTag(OPEN_EDITOR_TAG).assertDoesNotExist()
        PaletteEditorDismissFixture.assertClosedWithoutConfirmation(composeRule, controller)
    }

    /** Taps the scrim near the window edge away from the panel, which is aligned to the control edge. */
    private fun tapScrimOutsidePanel() {
        val close = composeRule.onNodeWithTag(CLOSE_TAG).fetchSemanticsNode().boundsInRoot
        val scrim = composeRule.onNodeWithTag(SCRIM_TAG)
        val scrimBounds = scrim.fetchSemanticsNode().boundsInRoot
        val panelOnRight = close.center.x > scrimBounds.center.x
        scrim.performTouchInput {
            click(Offset(if (panelOnRight) EDGE_INSET_PX else width - EDGE_INSET_PX, centerY))
        }
    }

    private companion object {
        const val SCRIM_TAG: String = "editor_dismiss_panel"
        const val DISCARD_TAG: String = "editor_palette_editor_discard_confirm"
        const val KEEP_TAG: String = "editor_palette_editor_discard_keep"
        const val EDGE_INSET_PX: Float = 4f
    }
}
