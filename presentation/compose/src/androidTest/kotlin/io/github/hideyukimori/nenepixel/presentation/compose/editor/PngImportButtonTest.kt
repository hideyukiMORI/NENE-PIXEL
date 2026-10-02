package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** ADR 0033 "Controls": the file surface offers Import PNG, and pressing it asks the PNG import port for a picture. */
internal class PngImportButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importPngOnTheFileSurfaceAsksThePngImportPort() {
        val picks = AtomicInteger()
        val port =
            PngImportPort {
                picks.incrementAndGet()
                PngImportOutcome.Cancelled
            }
        val controller = PresentationTestValues.fixture().controller
        composeRule.setContent { TestNenePixelEditor(controller, pngImport = port) }

        composeRule.onNodeWithTag("editor_file").performClick()
        val button = composeRule.onNodeWithTag(IMPORT_TAG)
        button.performScrollTo()
        button.assertIsEnabled()
        button.performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { picks.get() == 1 }
    }

    private companion object {
        const val IMPORT_TAG: String = "editor_import_png"
        const val TIMEOUT_MILLIS: Long = 5_000
    }
}
