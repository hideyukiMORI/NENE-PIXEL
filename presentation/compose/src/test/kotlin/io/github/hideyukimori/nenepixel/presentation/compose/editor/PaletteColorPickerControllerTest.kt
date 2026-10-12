package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.green
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.white
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Issue #168: real picker intents publish the canonical session and grouped draft history. */
internal class PaletteColorPickerControllerTest {
    @Test
    fun `one picker gesture publishes all previews and one undoable edit`() {
        val editor = fixture()
        val palette = editor.controller.callbacks.palette
        val original = palette.onBegin().paletteEditSession?.draft
        val picker = PaletteColorEditCallbacks(PaletteIndex.create(0).requiredValue(), palette)
        assertTrue(picker.begin())
        picker.update(green)
        picker.update(white)
        assertTrue(requireNotNull(editor.controller.renderState.paletteEditSession).isColorEditActive)
        assertSame(editor.controller.renderState, editor.controller.renderStates.value)
        picker.finish()
        val edited =
            editor.controller.renderState.paletteEditSession
                ?.draft
        assertEquals(original, palette.onUndo().paletteEditSession?.draft)
        assertFalse(requireNotNull(editor.controller.renderState.paletteEditSession).canUndoDraft)
        assertEquals(edited, palette.onRedo().paletteEditSession?.draft)
        assertEquals(editor.initialDocument.definition, editor.controller.renderState.definition)
    }

    @Test
    fun `competing numeric edits get a localized rejection and cancellation restores the draft`() {
        val editor = fixture()
        val palette = editor.controller.callbacks.palette
        val original = palette.onBegin().paletteEditSession?.draft
        val picker = PaletteColorEditCallbacks(PaletteIndex.create(0).requiredValue(), palette)
        assertTrue(picker.begin())
        picker.update(green)
        picker.commit(white)
        assertEquals(
            R.string.palette_notice_color_edit_active,
            editor.controller.renderState.paletteNotice
                ?.noticeResource(),
        )
        picker.cancel()
        assertNull(editor.controller.renderState.paletteNotice)
        assertEquals(
            original,
            editor.controller.renderState.paletteEditSession
                ?.draft,
        )
        assertFalse(requireNotNull(editor.controller.renderState.paletteEditSession).canUndoDraft)
    }
}
