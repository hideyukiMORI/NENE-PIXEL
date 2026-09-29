package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The layer panel's rows: order, selection, the visibility toggle and the scroll to the active row (#144 U4). */
internal class LayerPanelRowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rowsRunFromTheFrontLayerToTheBackLayer() {
        val controller = shownController(layers = 3)
        openPanel()
        val tops = frontToBack(controller).map { id -> row(id).assertIsDisplayed().getUnclippedBoundsInRoot().top }
        assertTrue("Rows must run front to back from the top: $tops", tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun tappingARowSelectsItsLayerAndTheChipFollows() {
        val controller = shownController(layers = 3)
        val back = frontToBack(controller).last()
        openPanel()
        row(back).performClick()
        composeRule.waitForIdle()
        assertEquals(back, controller.renderState.activeLayerId)
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHIP_TAG).assert(hasText(defaultName(back)))
    }

    @Test
    fun onlyTheActiveRowReadsAsCurrent() {
        val controller = shownController(layers = 3)
        openPanel()
        val active = controller.renderState.activeLayerId
        row(active).assert(stateDescription(text(R.string.layer_row_state_current)))
        frontToBack(controller).filter { id -> id != active }.forEach { id ->
            row(id).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }

    @Test
    fun theToggleFlipsVisibilityAndSaysWhatATapDoes() {
        val controller = shownController(layers = 3)
        val back = frontToBack(controller).last()
        openPanel()
        toggle(back).assert(description(text(R.string.layer_hide, defaultName(back)))).performClick()
        composeRule.waitForIdle()
        assertEquals(LayerVisibility.Hidden, visibility(controller, back))
        toggle(back).assert(description(text(R.string.layer_show, defaultName(back)))).performClick()
        composeRule.waitForIdle()
        assertEquals(LayerVisibility.Visible, visibility(controller, back))
        toggle(back).assert(description(text(R.string.layer_hide, defaultName(back))))
    }

    @Test
    fun undoRestoresTheVisibilityTheToggleChanged() {
        val controller = shownController(layers = 3)
        val back = frontToBack(controller).last()
        openPanel()
        toggle(back).performClick()
        composeRule.waitForIdle()
        assertEquals(LayerVisibility.Hidden, visibility(controller, back))
        composeRule.runOnIdle { controller.callbacks.onUndo() }
        composeRule.waitForIdle()
        assertEquals(LayerVisibility.Visible, visibility(controller, back))
        toggle(back).assert(description(text(R.string.layer_hide, defaultName(back))))
    }

    @Test
    fun aHiddenRowCanStillBecomeTheActiveLayer() {
        val controller = shownController(layers = 2)
        val back = frontToBack(controller).last()
        openPanel()
        toggle(back).performClick()
        composeRule.waitForIdle()
        row(back).performClick()
        composeRule.waitForIdle()
        assertEquals(back, controller.renderState.activeLayerId)
        assertEquals(LayerVisibility.Hidden, visibility(controller, back))
        row(back).assert(stateDescription(text(R.string.layer_row_state_current)))
    }

    @Test
    fun openingSixteenLayersShowsTheActiveRow() {
        val controller = shownController(layers = MAX_LAYERS)
        val back = frontToBack(controller).last()
        composeRule.runOnIdle { controller.callbacks.layers.onSelect(back) }
        composeRule.waitForIdle()
        openPanel()
        row(back).assertIsDisplayed()
    }

    private fun frontToBack(controller: EditorController): List<LayerId> =
        controller.renderState.document.layers
            .asReversed()
            .map { layer -> layer.id }

    private fun visibility(
        controller: EditorController,
        id: LayerId,
    ): LayerVisibility {
        val layers = controller.renderState.document.layers
        val layer = layers.first { candidate -> candidate.id == id }
        return layer.visibility
    }

    private fun row(id: LayerId) = composeRule.onNodeWithTag(ROW_TAG_PREFIX + id.value)

    private fun toggle(id: LayerId) = composeRule.onNodeWithTag(VISIBILITY_TAG_PREFIX + id.value)

    private fun openPanel() {
        composeRule.onNodeWithTag(CHIP_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun shownController(layers: Int): EditorController {
        val controller = controller()
        composeRule.setContent {
            Box(Modifier.requiredSize(WIDE_EDGE, TALL_EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(WIDE_EDGE, TALL_EDGE))
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { repeat(layers - 1) { controller.callbacks.layers.onAdd() } }
        composeRule.waitForIdle()
        return controller
    }

    private fun controller(): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val palette = Palette.create(listOf(opaque(CHANNEL_MAX, 0), opaque(0, CHANNEL_MAX))).requiredValue()
        val definition = PaletteDefinition.create(palette, PaletteIndex.create(0).requiredValue()).requiredValue()
        return EditorController.create(EditorRuntime.create(size, definition, FixedRowsDocumentIdSource))
    }

    private fun opaque(
        red: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(0).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(CHANNEL_MAX).requiredValue(),
        )

    private fun defaultName(id: LayerId): String = text(R.string.layer_default_name, id.value)

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun stateDescription(text: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun description(text: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid layer-row fixture: $rejection")
        }

    private companion object {
        val WIDE_EDGE: Dp = 600.dp
        val TALL_EDGE: Dp = 400.dp
        const val CHIP_TAG: String = "editor_layer_chip"
        const val CLOSE_TAG: String = "editor_layer_panel_close"
        const val ROW_TAG_PREFIX: String = "editor_layer_row_"
        const val VISIBILITY_TAG_PREFIX: String = "editor_layer_visibility_"
        const val DOCUMENT_WIDTH: Int = 64
        const val DOCUMENT_HEIGHT: Int = 48
        const val MAX_LAYERS: Int = 16
        const val CHANNEL_MAX: Int = 255
    }
}

private object FixedRowsDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("5".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid layer-row fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
