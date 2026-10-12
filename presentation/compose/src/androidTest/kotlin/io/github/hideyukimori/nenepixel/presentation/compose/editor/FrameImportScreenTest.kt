package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class FrameImportScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pendingDialogNamesItsCapturedFrameAndAppliesThereAfterFocusChanges() {
        val controller = PresentationTestValues.fixture().controller
        val first = controller.renderState.activeFrameId
        controller.callbacks.frames.onAdd(first)
        val second = controller.renderState.activeFrameId
        controller.callbacks.frames.onSelect(first)
        val raster = ImportRaster.create(1, 1, intArrayOf(RED)).frameValue()
        val configuration =
            TestPersistenceConfiguration(
                images = TestImageImportPorts(pngImport = PngImportPort { PngImportOutcome.Picked(raster) }),
            )
        rule.setContent { TestNenePixelEditor(controller, configuration = configuration) }
        rule.waitForIdle()
        FrameScreenFixture.click(rule, "editor_file")
        rule.onNodeWithTag("editor_import_png").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil { controller.renderState.pendingImport != null }
        rule.runOnIdle { controller.callbacks.frames.onSelect(second) }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        rule.onNodeWithText(resources.getString(R.string.png_import_target_frame, 1)).assertIsDisplayed()
        rule.onNodeWithTag("editor_png_import_append").performScrollTo().performClick()
        rule.waitUntil { controller.renderState.pendingImport == null }
        assertEquals(2, requireNotNull(controller.documentState.frameById(first)).layers.size)
        assertEquals(1, requireNotNull(controller.documentState.frameById(second)).layers.size)
        assertEquals(first, controller.renderState.activeFrameId)
    }

    private companion object {
        const val RED = 0xFF0000FF.toInt()
    }
}
