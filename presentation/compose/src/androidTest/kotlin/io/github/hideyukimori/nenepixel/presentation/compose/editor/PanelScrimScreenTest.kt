package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Issue #148 P3 (plan B): the panel scrim is clear only while the palette editor is open, so the canvas
 * behind it shows the draft colours without the 45% dim. The scrim node is read back from the Dialog's
 * own window, a few pixels inside its left edge where the panel's 8 dp padding keeps the scrim uncovered
 * on either control edge. The window's own dim (if any) is composited outside that window, so it is not
 * in the image; the scrim tests fix only the scrim layer. P3b reads the window's dim amount directly.
 */
internal class PanelScrimScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thePaletteEditorScrimIsClear() {
        showEditor()
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_cancel").assertExists()

        assertEquals("The palette editor scrim has colour", 0, scrimAlpha())
    }

    @Test
    fun thePaletteScrimKeepsItsDim() {
        showEditor()
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").assertIsDisplayed()

        val alpha = scrimAlpha()
        assertEquals("The palette scrim lost its dim (alpha $alpha)", DIM_ALPHA.toFloat(), alpha.toFloat(), TOLERANCE)
    }

    @Test
    fun thePaletteEditorWindowDoesNotDim() {
        showEditor()
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_cancel").assertExists()

        assertEquals("The palette editor window dims the canvas", 0f, windowDimAmount(), 0f)
    }

    @Test
    fun thePaletteWindowKeepsItsDim() {
        showEditor()
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").assertIsDisplayed()

        val dim = windowDimAmount()
        assertTrue("The palette window lost its dim ($dim)", dim > 0f)
    }

    private fun showEditor() {
        val editor = fixture(canvas(WIDTH, HEIGHT))
        composeRule.setContent {
            Box(Modifier.requiredSize(EDGE_WIDTH, EDGE_HEIGHT).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(editor.controller, Modifier.requiredSize(EDGE_WIDTH, EDGE_HEIGHT))
            }
        }
        composeRule.waitForIdle()
    }

    /** The scrim's alpha (0-255) at the vertical middle, [EDGE_INSET] device pixels inside the left edge. */
    private fun scrimAlpha(): Int {
        val image = composeRule.onNodeWithTag(SCRIM_TAG).captureToImage().toPixelMap()
        val sample: Color = image[EDGE_INSET, image.height / 2]
        return (sample.alpha * MAX_CHANNEL).roundToInt()
    }

    /** The Dialog window's dim amount, read on the main thread through the scrim node's compose root. */
    private fun windowDimAmount(): Float {
        val root = composeRule.onNodeWithTag(SCRIM_TAG).fetchSemanticsNode().root as ViewRootForTest
        return composeRule.runOnIdle {
            val window = (root.view.parent as DialogWindowProvider).window
            window.attributes.dimAmount
        }
    }

    private companion object {
        const val WIDTH: Int = 3
        const val HEIGHT: Int = 2
        const val SCRIM_TAG: String = "editor_dismiss_panel"
        const val EDGE_INSET: Int = 2
        const val MAX_CHANNEL: Float = 255f
        const val TOLERANCE: Float = 2f
        val DIM_ALPHA: Int = (0.45f * MAX_CHANNEL).roundToInt()
        val EDGE_WIDTH: Dp = 720.dp
        val EDGE_HEIGHT: Dp = 600.dp
    }
}
