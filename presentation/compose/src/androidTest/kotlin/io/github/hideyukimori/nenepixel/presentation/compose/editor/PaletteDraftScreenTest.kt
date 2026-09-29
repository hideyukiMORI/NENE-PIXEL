package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Issue #148 P2t: the screen itself shows the draft. While the palette editor Dialog is open, the canvas
 * and the actual-size window are read back from the activity window (`captureToImage` copies the window
 * that holds the node, so the Dialog's scrim is not in the image) at every document pixel centre, and
 * must equal what they show after the real Apply. This fails if either surface is not given the session.
 */
internal class PaletteDraftScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theCanvasAndTheActualSizeWindowShowTheDraftBehindTheEditor() {
        val editor = paintedEditor()
        val original = shownSamples(editor)
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").performClick()
        assertNotNull(editor.controller.renderState.paletteEditSession)
        editorNode("editor_palette_editor_slot_2").performClick()
        editorNode("editor_palette_editor_hex").performTextReplacement(MAGENTA_HEX)
        composeRule.onNodeWithTag("editor_palette_editor_hex").performImeAction()

        val drafted = shownSamples(editor)
        editorNode("editor_palette_editor_apply").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_apply").assertDoesNotExist()
        assertNull(editor.controller.renderState.paletteEditSession)
        val applied = shownSamples(editor)

        assertEquals("The canvas showed green before the draft", OPAQUE_GREEN, original[GREEN_PIXEL])
        assertEquals("The window showed green before the draft", OPAQUE_GREEN, original[WINDOW_OFFSET + GREEN_PIXEL])
        assertEquals("The canvas shows the draft colour", OPAQUE_MAGENTA, drafted[GREEN_PIXEL])
        assertEquals("The window shows the draft colour", OPAQUE_MAGENTA, drafted[WINDOW_OFFSET + GREEN_PIXEL])
        assertArrayEquals("The screen showed the applied picture while drafting", applied, drafted)
    }

    /**
     * The canvas at every document pixel centre with the actual-size window hidden, followed by the
     * window's content at every pixel centre; the window is hidden again afterwards.
     */
    private fun shownSamples(editor: EditorFixture): IntArray {
        setWindowVisible(editor, false)
        val canvas = canvasSamples(editor)
        setWindowVisible(editor, true)
        val image = composeRule.onNodeWithTag(CONTENT_TAG).captureToImage().toPixelMap()
        val factor = WINDOW_SCALE.devicePixelsPerCell
        val window = pixels().map { (x, y) -> image[x * factor + factor / 2, y * factor + factor / 2].toArgb() }
        setWindowVisible(editor, false)
        return canvas + window.toIntArray()
    }

    private fun canvasSamples(editor: EditorFixture): IntArray {
        val image = composeRule.onNodeWithTag(CANVAS_TAG).captureToImage().toPixelMap()
        val density = composeRule.density.density.toDouble()
        val surface =
            when (val result = ViewportSurface.create(image.width, image.height, density)) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid test surface: ${result.rejection}")
            }
        val state = editor.controller.renderState
        val transform = checkNotNull(createViewportTransform(state.document.size, surface, state.viewport))
        return pixels()
            .map { (x, y) ->
                val bounds = checkNotNull(transform.surfaceBounds(pixel(x, y)))
                image[((bounds.left + bounds.right) / 2.0).toInt(), ((bounds.top + bounds.bottom) / 2.0).toInt()]
                    .toArgb()
            }.toIntArray()
    }

    private fun setWindowVisible(
        editor: EditorFixture,
        visible: Boolean,
    ) {
        composeRule.runOnIdle {
            val current = editor.controller.renderState.actualSizeWindow
            val shown = if (current.visible == visible) current else current.toggled()
            editor.controller.callbacks.onSetActualSizeWindow(shown.withScale(WINDOW_SCALE))
        }
        composeRule.waitForIdle()
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
        val effect = StrokeEffect.Paint(PaletteIndex.create(index).requiredValue())
        val stroke = Stroke.create(runtime.state.documentState.size, listOf(pixel(index, 0)), effect).requiredValue()
        runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), LayerId.first(), stroke))
        editor.controller.synchronizeWithRuntime()
    }

    private fun editorNode(identity: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(identity).performScrollTo()

    private companion object {
        const val WIDTH: Int = 3
        const val HEIGHT: Int = 2
        const val WINDOW_OFFSET: Int = WIDTH * HEIGHT
        const val GREEN_PIXEL: Int = 1
        const val CANVAS_TAG: String = "editor_canvas_3_2"
        const val CONTENT_TAG: String = "editor_actual_size_window_content"
        const val MAGENTA_HEX: String = "#FF00FFFF"
        const val OPAQUE_GREEN: Int = 0xFF00FF00.toInt()
        const val OPAQUE_MAGENTA: Int = 0xFFFF00FF.toInt()
        val WINDOW_SCALE: ActualSizeScale = ActualSizeScale.X8
        val EDGE_WIDTH: Dp = 720.dp
        val EDGE_HEIGHT: Dp = 600.dp
        val BLUE: PixelColor = PixelColor.fromPackedRgba8888(0x0000FFFF)

        fun pixels(): List<Pair<Int, Int>> = (0 until HEIGHT).flatMap { y -> (0 until WIDTH).map { x -> x to y } }

        fun pixel(
            x: Int,
            y: Int,
        ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())
    }
}

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid palette draft screen fixture: $rejection")
    }
