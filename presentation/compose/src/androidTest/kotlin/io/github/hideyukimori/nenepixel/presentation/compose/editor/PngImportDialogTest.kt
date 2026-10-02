package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.content.res.Resources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * ADR 0033 "Controls": after a PNG is picked, one dialog states its size and colours and offers the two layer forms
 * and Cancel; each form states what it does or why it is unavailable, and pressing it acts at once.
 */
internal class PngImportDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val resources: Resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun theDialogStatesTheSizeAndTheNumberOfColours() {
        open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED))

        composeRule.onNodeWithText(resources.getString(R.string.png_import_size, 3, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.png_import_colors, 2, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.png_import_appended, 1, 1)).assertExists()
        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.png_import_nearest, 1, 1)).assertExists()
    }

    @Test
    fun appendingAddsALayerAndItsColourAndClosesTheDialog() {
        val controller = open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED))

        composeRule.onNodeWithTag(APPEND_TAG).performScrollTo().performClick()

        awaitClosed()
        assertEquals(2, controller.renderState.document.layers.size)
        assertEquals(PALETTE_SIZE + 1, controller.renderState.palette.entryCount)
    }

    @Test
    fun convertingAddsALayerAndKeepsThePalette() {
        val controller = open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED))

        composeRule.onNodeWithTag(CONVERT_TAG).performScrollTo().performClick()

        awaitClosed()
        assertEquals(2, controller.renderState.document.layers.size)
        assertEquals(PALETTE_SIZE, controller.renderState.palette.entryCount)
    }

    @Test
    fun cancellingClosesTheDialogAndKeepsTheDocument() {
        val controller = open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED))
        val before = controller.renderState.document

        composeRule.onNodeWithTag(CANCEL_TAG).performScrollTo().performClick()

        awaitClosed()
        assertSame(before, controller.renderState.document)
    }

    @Test
    fun aTransparentPictureDisablesBothFormsAndSaysNothingIsImported() {
        open(raster(2, 2, TRANSPARENT, TRANSPARENT, TRANSPARENT, TRANSPARENT))

        composeRule.onNodeWithTag(APPEND_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(CONVERT_TAG).assertIsNotEnabled()
        composeRule.onAllNodesWithText(resources.getString(R.string.png_import_nothing)).assertCountEquals(2)
    }

    @Test
    fun aPictureWiderThanTheCanvasStatesTheDroppedPixels() {
        open(raster(6, 2, *IntArray(12) { RED }))

        val dropped = resources.getQuantityString(R.plurals.png_import_dropped, 4, 4)
        composeRule.onAllNodesWithText(dropped).assertCountEquals(2)
    }

    @Test
    fun sixteenLayersDisableBothFormsAndStateTheLimit() {
        open(raster(3, 2, RED, BLUE, RED, BLUE, RED, RED), layers = MAX_LAYERS)

        composeRule.onNodeWithTag(APPEND_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(CONVERT_TAG).assertIsNotEnabled()
        val limit = resources.getQuantityString(R.plurals.layer_limit, MAX_LAYERS, MAX_LAYERS)
        composeRule.onAllNodesWithText(limit).assertCountEquals(2)
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
            composeRule.onAllNodesWithTag(APPEND_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        return controller
    }

    private fun awaitClosed() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(APPEND_TAG).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val FILE_TAG: String = "editor_file"
        const val IMPORT_TAG: String = "editor_import_png"
        const val APPEND_TAG: String = "editor_png_import_append"
        const val CONVERT_TAG: String = "editor_png_import_convert"
        const val CANCEL_TAG: String = "editor_png_import_cancel"
        const val TIMEOUT_MILLIS: Long = 5_000
        const val PALETTE_SIZE: Int = 9
        const val MAX_LAYERS: Int = 16
        const val RED: Int = 0xFF0000FF.toInt()
        const val BLUE: Int = 0x0000FFFF
        const val TRANSPARENT: Int = 0

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
