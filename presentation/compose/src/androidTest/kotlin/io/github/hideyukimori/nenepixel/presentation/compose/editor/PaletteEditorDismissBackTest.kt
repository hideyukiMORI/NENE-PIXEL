package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PaletteEditorDismissFixture.CANCEL_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.PaletteEditorDismissFixture.DIALOG_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Issue #165 S2b: Back on the palette editor ends an unchanged draft at once (ruling 3); on a changed draft the
 * first Back asks and a second Back closes only the confirmation (ruling 6). Back is a real key event so it
 * reaches the focused panel or confirmation window, not the activity's dispatcher.
 */
internal class PaletteEditorDismissBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unchangedDraftBackEndsTheSessionWithoutConfirmation() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        pressBack()
        PaletteEditorDismissFixture.assertClosedWithoutConfirmation(composeRule, controller)
    }

    @Test
    fun changedDraftBackAsksThenSecondBackClosesOnlyTheConfirmation() {
        val controller = PaletteEditorDismissFixture.shownController(composeRule)
        val before = controller.renderState.definition
        PaletteEditorDismissFixture.openPaletteEditor(composeRule)
        val edited = PaletteEditorDismissFixture.changeDraftWithoutKeyboard(composeRule, controller)
        pressBack()
        composeRule.onNodeWithTag(DIALOG_TAG).assertExists()
        pressBack()
        composeRule.onNodeWithTag(DIALOG_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CANCEL_TAG).assertExists()
        assertEquals(edited, controller.renderState.paletteEditSession?.draft)
        assertNotEquals(before, edited)
        assertEquals(before, controller.renderState.definition)
    }

    /** Sends Back from the test thread; the instrumentation call must not run on the main thread. */
    private fun pressBack() {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }
}
