package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.ADD_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.MAX_LAYERS
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.click
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.openPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The layer panel's underlay row (#170 A7): choose, show and hide, opacity, fit, remove, and 16 layers, where the row
 * scrolls with the layer rows while the add row stays in view.
 */
internal class LayerPanelUnderlayRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun withoutAnUnderlayThePanelShowsOnlyThePickButton() {
        show()
        openPanel(composeRule)
        composeRule.onNodeWithTag(PICK_TAG).assertIsDisplayed()
        listOf(VISIBILITY_TAG, MORE_TAG, OPACITY_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    @Test
    fun pickingShowsThePickedImageAndTheFullRow() {
        val controller = show()
        openPanel(composeRule)
        click(composeRule, PICK_TAG)
        composeRule.waitUntil(TIMEOUT_MILLIS) { controller.renderState.underlay != null }
        assertEquals(image, controller.renderState.underlay?.image)
        composeRule.onNodeWithTag(PICK_TAG).assertDoesNotExist()
        listOf(VISIBILITY_TAG, MORE_TAG, OPACITY_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun theEyeHidesTheUnderlayAndDisablesTheSliderThenShowsItAgain() {
        val controller = showWithUnderlay()
        click(composeRule, VISIBILITY_TAG)
        assertEquals(UnderlayVisibility.Hidden, controller.renderState.underlay?.visibility)
        composeRule.onNodeWithTag(OPACITY_TAG).assertIsNotEnabled()
        click(composeRule, VISIBILITY_TAG)
        assertEquals(UnderlayVisibility.Shown, controller.renderState.underlay?.visibility)
        composeRule.onNodeWithTag(OPACITY_TAG).assertIsEnabled()
    }

    @Test
    fun movingTheSliderChangesTheAlpha() {
        val controller = showWithUnderlay()
        composeRule.onNodeWithTag(OPACITY_TAG).performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(SLIDER_ALPHA.toFloat())
        }
        composeRule.waitForIdle()
        assertEquals(SLIDER_ALPHA, requireNotNull(controller.renderState.underlay).opacity.alpha)
    }

    @Test
    fun fitRestoresTheFittedPlacementAndRemoveReturnsToThePickButton() {
        val controller = showWithUnderlay()
        val moved = requireNotNull(controller.renderState.underlay).withPlacement(MOVED_LEFT, MOVED_TOP, MOVED_SCALE)
        composeRule.runOnIdle { controller.callbacks.underlay.onSet(moved) }
        assertNotEquals(fittedPlacement(controller), controller.renderState.underlay?.placement)
        click(composeRule, MORE_TAG)
        click(composeRule, FIT_TAG)
        assertEquals(fittedPlacement(controller), controller.renderState.underlay?.placement)
        click(composeRule, MORE_TAG)
        click(composeRule, REMOVE_TAG)
        assertNull(controller.renderState.underlay)
        composeRule.onNodeWithTag(PICK_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(VISIBILITY_TAG).assertDoesNotExist()
    }

    @Test
    fun sixteenLayersKeepTheAddRowInViewAndTheUnderlayRowScrollsIntoView() {
        val controller = showWithUnderlay()
        composeRule.runOnIdle { repeat(MAX_LAYERS - 1) { controller.callbacks.layers.onAdd() } }
        composeRule.waitForIdle()
        assertEquals(MAX_LAYERS, controller.renderState.document.layers.size)
        composeRule.onNodeWithTag(ADD_TAG).assertIsDisplayed()
        listOf(VISIBILITY_TAG, OPACITY_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    /** Shows the editor with one layer; its reference-image port always picks [image]. */
    private fun show(): EditorController {
        val controller = PresentationTestValues.fixture(PresentationTestValues.canvas(WIDTH, HEIGHT)).controller
        val port = ReferenceImagePort { ReferenceImageOutcome.Picked(image) }
        composeRule.setContent {
            Box(Modifier.requiredSize(EDGE, EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(EDGE, EDGE), referenceImage = port)
            }
        }
        composeRule.waitForIdle()
        return controller
    }

    /** Shows the editor with [image] placed as the underlay and the panel open. */
    private fun showWithUnderlay(): EditorController {
        val controller = show()
        val underlay = ReferenceUnderlay.placed(image, controller.renderState.document.size)
        composeRule.runOnIdle { controller.callbacks.underlay.onSet(underlay) }
        openPanel(composeRule)
        return controller
    }

    private fun fittedPlacement(controller: EditorController): UnderlayPlacement =
        UnderlayPlacement.fitted(image, controller.renderState.document.size)

    private val image = UnderlayDisplayFixture.greenImage(IMAGE_WIDTH, IMAGE_HEIGHT)

    private companion object {
        const val PICK_TAG: String = "editor_underlay_pick"
        const val VISIBILITY_TAG: String = "editor_underlay_visibility"
        const val MORE_TAG: String = "editor_underlay_more"
        const val OPACITY_TAG: String = "editor_underlay_opacity"
        const val FIT_TAG: String = "editor_underlay_fit"
        const val REMOVE_TAG: String = "editor_underlay_remove"
        const val WIDTH: Int = 64
        const val HEIGHT: Int = 48
        const val IMAGE_WIDTH: Int = 8
        const val IMAGE_HEIGHT: Int = 4
        const val SLIDER_ALPHA: Int = 200
        const val MOVED_LEFT: Double = 5.0
        const val MOVED_TOP: Double = 7.0
        const val MOVED_SCALE: Double = 3.0
        const val TIMEOUT_MILLIS: Long = 5_000
        val EDGE = 600.dp
    }
}
