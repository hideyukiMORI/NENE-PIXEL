package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftComposite
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeResult
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Issue #148 P2: while the palette editor is open, the committed bitmap shows the picture the draft
 * would give once applied (ADR 0022), rendered through the one application Composite. The document
 * itself is unchanged until Apply; Cancel returns to the document's own picture.
 */
internal class PaletteDraftDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRecolouredSlotIsShownFromTheDraft() {
        val editor = paintedEditor()
        val original = shownPixels(editor)
        openPaletteEditor(editor)
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)

        assertDraftShown(editor)
        assertFalse("The recolour must change the picture", original.contentEquals(shownPixels(editor)))
    }

    @Test
    fun aRemovedSlotIsShownFromTheDraft() {
        val editor = paintedEditor()
        openPaletteEditor(editor)
        editorNode("editor_palette_editor_slot_2").performClick()
        editorNode("editor_palette_editor_remove").performClick()

        assertDraftShown(editor)
    }

    @Test
    fun aReorderedDraftKeepsThePicture() {
        val editor = paintedEditor()
        val original = shownPixels(editor)
        openPaletteEditor(editor)
        editorNode("editor_palette_editor_slot_2").performClick()
        editorNode("editor_palette_editor_move_down").performClick()

        assertDraftShown(editor)
        assertArrayEquals("A reorder keeps every pixel's colour", original, shownPixels(editor))
    }

    @Test
    fun cancelReturnsToTheDocumentPicture() {
        val editor = paintedEditor()
        val original = shownPixels(editor)
        openPaletteEditor(editor)
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)
        editorNode("editor_palette_editor_cancel").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_cancel").assertDoesNotExist()

        assertNull(editor.controller.renderState.paletteEditSession)
        assertArrayEquals("Cancel shows the document's own picture", original, shownPixels(editor))
    }

    @Test
    fun applyKeepsTheShownPicture() {
        val editor = paintedEditor()
        openPaletteEditor(editor)
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)
        val drafted = shownPixels(editor)
        editorNode("editor_palette_editor_apply").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_apply").assertDoesNotExist()

        assertNull(editor.controller.renderState.paletteEditSession)
        assertArrayEquals("Apply shows the picture the draft showed", drafted, shownPixels(editor))
    }

    @Test
    fun theSameSessionReusesTheBitmapAndANewSessionRebuildsIt() {
        val editor = paintedEditor()
        openPaletteEditor(editor)
        val committed = CommittedBitmapCache()
        val opened = editor.controller.renderState
        val first = committed.render(opened.document, opened.definition, opened.paletteEditSession)
        val again = committed.render(opened.document, opened.definition, opened.paletteEditSession)
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)
        val edited = editor.controller.renderState
        assertNotSame(opened.paletteEditSession, edited.paletteEditSession)
        val rebuilt = committed.render(edited.document, edited.definition, edited.paletteEditSession)

        assertSame("Unchanged inputs keep the bitmap", first, again)
        assertNotSame("A new draft session rebuilds the bitmap", first, rebuilt)
    }

    /** The committed bitmap equals the P1 draft Composite reordered to ARGB. */
    private fun assertDraftShown(editor: EditorFixture) {
        val state = editor.controller.renderState
        val session = requireNotNull(state.paletteEditSession) { "No palette edit session" }
        val expected =
            when (val result = PaletteDraftComposite.render(state.document, session)) {
                is PaletteDraftCompositeResult.Rendered -> result.image.toStraightArgb()
                PaletteDraftCompositeResult.SourceMismatch -> error("The draft must start from the document palette")
            }
        assertArrayEquals("The canvas shows the draft picture", expected, shownPixels(editor))
    }

    private fun shownPixels(editor: EditorFixture): IntArray {
        val state = editor.controller.renderState
        return CommittedBitmapCache().render(state.document, state.definition, state.paletteEditSession).pixels()
    }

    /** Red at (0, 0), green at (1, 0), blue at (2, 0); every other pixel is Empty. */
    private fun paintedEditor(): EditorFixture {
        val colors =
            listOf(PresentationTestValues.red, PresentationTestValues.green, BLUE, PresentationTestValues.transparent)
        val editor = fixture(canvas(WIDTH, HEIGHT), colors)
        listOf(0, 1, 2).forEach { index -> paint(editor, index) }
        composeRule.setContent {
            Box(Modifier.requiredSize(EDGE_WIDTH, EDGE_HEIGHT).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(editor.controller, Modifier.requiredSize(EDGE_WIDTH, EDGE_HEIGHT))
            }
        }
        composeRule.waitForIdle()
        return editor
    }

    private fun paint(
        editor: EditorFixture,
        index: Int,
    ) {
        val runtime = editor.runtime
        val position = PixelPosition.create(PixelX.create(index).requiredValue(), PixelY.create(0).requiredValue())
        val effect = StrokeEffect.Paint(PaletteIndex.create(index).requiredValue())
        val stroke = Stroke.create(runtime.state.documentState.size, listOf(position), effect).requiredValue()
        runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), LayerId.first(), stroke))
        editor.controller.synchronizeWithRuntime()
    }

    private fun openPaletteEditor(editor: EditorFixture) {
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_cancel").assertExists()
        requireNotNull(editor.controller.renderState.paletteEditSession) { "The palette editor did not open" }
    }

    private fun editorNode(identity: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(identity).performScrollTo()

    private fun enterHex(hex: String) {
        editorNode("editor_palette_editor_hex").performTextReplacement(hex)
        composeRule.onNodeWithTag("editor_palette_editor_hex").performImeAction()
    }

    private fun Bitmap.pixels(): IntArray {
        val actual = IntArray(width * height)
        getPixels(actual, 0, width, 0, 0, width, height)
        return actual
    }

    private companion object {
        const val WIDTH: Int = 3
        const val HEIGHT: Int = 2
        const val EDITED_HEX: String = "#FF00FF80"
        val EDGE_WIDTH: Dp = 720.dp
        val EDGE_HEIGHT: Dp = 600.dp
        val BLUE: PixelColor =
            PixelColor.create(
                ColorChannel.create(0).requiredValue(),
                ColorChannel.create(0).requiredValue(),
                ColorChannel.create(255).requiredValue(),
                ColorChannel.create(255).requiredValue(),
            )
    }
}

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid palette draft display fixture: $rejection")
    }
