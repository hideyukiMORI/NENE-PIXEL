package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.content.res.Resources
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * ADR 0033 "Controls": the third form of the PNG import dialog opens the picked PNG as a new work. It states that the
 * current work closes, or why it is unavailable; unsaved changes ask for the switch confirmation first, and the layer
 * limit does not apply to it.
 */
internal class PngImportNewWorkDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val resources: Resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun aCleanWorkOpensTheSmallPictureAsANewWork() {
        val controller = open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED))

        composeRule.onNodeWithTag(NEW_WORK_TAG).assertIsEnabled()
        composeRule.onNodeWithText(resources.getString(R.string.png_import_new_work_note)).assertExists()
        composeRule.onNodeWithTag(NEW_WORK_TAG).performScrollTo().performClick()

        awaitClosed()
        awaitNewWork(controller)
    }

    @Test
    fun aDirtyWorkAsksFirstAndTheConfirmationOpensTheNewWork() {
        val controller = open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED), layers = 2)

        composeRule.onNodeWithTag(NEW_WORK_TAG).performScrollTo().performClick()

        awaitClosed()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(DISCARD_CONTINUE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, controller.renderState.document.layers.size)
        composeRule.onNodeWithTag(DISCARD_CONTINUE_TAG).performClick()
        awaitNewWork(controller)
    }

    @Test
    fun aPictureWiderThanTheLimitCannotOpenAsANewWorkButCanBeAppended() {
        open(raster(LIMIT + 1, 1, *IntArray(LIMIT + 1) { RED }))

        composeRule.onNodeWithTag(NEW_WORK_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText(resources.getString(R.string.png_import_new_work_too_large)).assertExists()
        composeRule.onNodeWithTag(APPEND_TAG).assertIsEnabled()
    }

    @Test
    fun aPictureWithTooManyColoursCannotOpenAsANewWork() {
        open(raster(TOO_MANY_WIDTH, 2, *IntArray(TOO_MANY_WIDTH * 2) { opaque(it % (LIMIT + 1)) }))

        composeRule.onNodeWithTag(NEW_WORK_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText(resources.getString(R.string.png_import_new_work_too_many_colors)).assertExists()
    }

    @Test
    fun sixteenLayersStillAllowOpeningAsANewWork() {
        open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED), layers = MAX_LAYERS)

        composeRule.onNodeWithTag(APPEND_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(CONVERT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(NEW_WORK_TAG).assertIsEnabled()
    }

    /** Shows the editor with [layers] layers, presses Import PNG on the file surface, and waits for the dialog. */
    private fun open(
        picked: ImportRaster,
        layers: Int = 1,
    ): EditorController {
        val controller = PresentationTestValues.fixture().controller
        val port = PngImportPort { PngImportOutcome.Picked(picked) }
        composeRule.setContent { TestNenePixelEditor(controller, pngImport = port) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { repeat(layers - 1) { controller.callbacks.layers.onAdd() } }
        composeRule.onNodeWithTag(FILE_TAG).performClick()
        composeRule.onNodeWithTag(IMPORT_TAG).performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(NEW_WORK_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        return controller
    }

    private fun awaitClosed() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(NEW_WORK_TAG).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Waits for the 3 x 2 red-and-blue PNG to become the work: its size, its two colours in order, one layer. */
    private fun awaitNewWork(controller: EditorController) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { controller.renderState.document.size.width.value == 3 }
        val state = controller.renderState
        assertEquals(2, state.document.size.height.value)
        assertEquals(
            listOf(PixelColor.fromPackedRgba8888(RED), PixelColor.fromPackedRgba8888(BLUE)),
            state.palette.entries().map { entry -> entry.color },
        )
        assertEquals(1, state.document.layers.size)
        assertNull(state.pendingImport)
    }

    private companion object {
        const val FILE_TAG: String = "editor_file"
        const val IMPORT_TAG: String = "editor_import_png"
        const val APPEND_TAG: String = "editor_png_import_append"
        const val CONVERT_TAG: String = "editor_png_import_convert"
        const val NEW_WORK_TAG: String = "editor_png_import_new_work"
        const val DISCARD_CONTINUE_TAG: String = "editor_discard_continue"
        const val TIMEOUT_MILLIS: Long = 5_000
        const val MAX_LAYERS: Int = 16
        const val LIMIT: Int = 256
        const val TOO_MANY_WIDTH: Int = 129
        const val RED: Int = 0xFF0000FF.toInt()
        const val BLUE: Int = 0x0000FFFF
        const val OPAQUE: Int = 0xFF
        const val CHANNEL_SHIFT: Int = 8

        /** An opaque colour that differs for every [index] below 2^24. */
        fun opaque(index: Int): Int = ((index + 1) shl CHANNEL_SHIFT) or OPAQUE

        /** A raster of packed RGBA8888 [pixels], row-major. The fixture canvas is 4 x 4 and holds red, not blue. */
        fun raster(
            width: Int,
            height: Int,
            vararg pixels: Int,
        ): ImportRaster =
            when (val result = ImportRaster.create(width, height, pixels)) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Test raster is invalid: ${result.rejection}")
            }
    }
}
