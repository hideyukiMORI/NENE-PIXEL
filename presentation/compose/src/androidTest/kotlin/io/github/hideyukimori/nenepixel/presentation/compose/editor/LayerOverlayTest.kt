package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** The layer chip and the open/close of the layer panel over the work area (#144 U3, U3r). */
internal class LayerOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backDispatcher: OnBackPressedDispatcher? = null

    @Test
    fun theChipShowsTheActiveLayerDefaultName() {
        val controller = shownController()
        val label = text(R.string.layer_default_name, controller.renderState.activeLayerId.value)
        composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed().assert(hasText(label))
        composeRule.onNodeWithTag(CHIP_TAG).assert(stateDescription(text(R.string.layer_chip_state, label)))
        composeRule.onNodeWithTag(PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun theChipShowsTheActiveLayerOwnName() {
        val controller = shownController()
        composeRule.runOnIdle {
            val sky = LayerName.create(SKY).requiredValue()
            controller.callbacks.layers.onRename(controller.renderState.activeLayerId, sky)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHIP_TAG).assert(hasText(SKY))
    }

    @Test
    fun aHiddenActiveLayerReadsAsHidden() {
        val controller = shownController()
        composeRule.runOnIdle {
            controller.callbacks.layers.onSetVisibility(controller.renderState.activeLayerId, LayerVisibility.Hidden)
        }
        composeRule.waitForIdle()
        val label = text(R.string.layer_default_name, controller.renderState.activeLayerId.value)
        composeRule.onNodeWithTag(CHIP_TAG).assert(stateDescription(text(R.string.layer_chip_state_hidden, label)))
    }

    @Test
    fun tappingTheChipOpensThePanelInPlaceOfTheChip() {
        shownController()
        openPanel()
        composeRule.onNodeWithTag(PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PANEL_TAG).assert(paneTitle(text(R.string.layers)))
        composeRule
            .onNode(hasText(text(R.string.layer_count, 1, MAX_LAYERS)) and hasAnyAncestor(hasTestTag(PANEL_TAG)))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CHIP_TAG).assertDoesNotExist()
    }

    @Test
    fun theCloseButtonClosesThePanel() {
        shownController()
        openPanel()
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PANEL_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed()
    }

    @Test
    fun backClosesThePanelAndIsNotTakenWhileClosed() {
        shownController()
        composeRule.runOnIdle { assertFalse(requiredBackDispatcher().hasEnabledCallbacks()) }
        openPanel()
        composeRule.runOnIdle { requiredBackDispatcher().onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PANEL_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed()
    }

    @Test
    fun aTapOutsideThePanelDrawsAndKeepsThePanelOpen() {
        val controller = shownController()
        openPanel()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertTrue("The tap outside the panel must reach the canvas", controller.renderState.canUndo)
        composeRule.onNodeWithTag(PANEL_TAG).assertIsDisplayed()
    }

    @Test
    fun aTapOnThePanelNeverReachesTheCanvas() {
        val controller = shownController()
        openPanel()
        val bounds = composeRule.onNodeWithTag(PANEL_TAG).getUnclippedBoundsInRoot()
        val point =
            with(composeRule.density) {
                Offset(((bounds.left + bounds.right) / 2).toPx(), (bounds.bottom - PROBE_INSET).toPx())
            }
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()
        assertFalse("A tap on the panel must not draw", controller.renderState.canUndo)
        composeRule.onNodeWithTag(CLOSE_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()
        assertTrue("The probe point must be over the raster once the panel is closed", controller.renderState.canUndo)
    }

    @Test
    fun theChipSitsAtTheTopLeftOfTheWorkAreaForBothControlEdges() {
        val controller = shownController()
        EditorControlEdge.entries.forEach { edge ->
            setControlEdge(controller, edge)
            val area = composeRule.onNodeWithTag(CANVAS_TAG).getUnclippedBoundsInRoot()
            val chip = composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertNear("$edge: chip left margin", MARGIN, chip.left - area.left)
            assertTrue("$edge: chip $chip must start at the top of $area", chip.top - area.top <= MARGIN + TOLERANCE)
            val middle = (area.top + area.bottom) / 2
            assertTrue("$edge: chip $chip must stay in the top half of $area", chip.bottom < middle)
        }
    }

    @Test
    fun withTheLeftControlEdgeThePanelStopsAboveTheQuickSelectControl() {
        val controller = shownController(SHORT_EDGE)
        composeRule.runOnIdle {
            val appearance = controller.renderState.appearance
            controller.callbacks.onSetAppearance(appearance.copy(layout = EditorLayout.Handheld))
        }
        val bottoms =
            EditorControlEdge.entries.associateWith { edge ->
                setControlEdge(controller, edge)
                openPanel()
                val area = composeRule.onNodeWithTag(CANVAS_TAG).getUnclippedBoundsInRoot()
                val panel = composeRule.onNodeWithTag(PANEL_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
                composeRule.onNodeWithTag(CLOSE_TAG).performClick()
                composeRule.waitForIdle()
                area.bottom - panel.bottom
            }
        val left = bottoms.getValue(EditorControlEdge.Left)
        val right = bottoms.getValue(EditorControlEdge.Right)
        assertTrue("Left: the panel must end $LEFT_CLEARANCE above the area: $left", left >= LEFT_CLEARANCE - TOLERANCE)
        assertTrue("Right: the panel must end $PANEL_MARGIN above the area: $right", right >= PANEL_MARGIN - TOLERANCE)
        assertTrue("Right: the short area must let the panel pass the Left limit: $right", right < LEFT_CLEARANCE)
    }

    private fun setControlEdge(
        controller: EditorController,
        edge: EditorControlEdge,
    ) {
        composeRule.runOnIdle {
            controller.callbacks.onSetAppearance(controller.renderState.appearance.copy(controlEdge = edge))
        }
        composeRule.waitForIdle()
    }

    private fun assertNear(
        message: String,
        expected: Dp,
        actual: Dp,
    ) {
        assertTrue("$message: expected $expected, was $actual", abs((expected - actual).value) <= TOLERANCE.value)
    }

    private fun openPanel() {
        composeRule.onNodeWithTag(CHIP_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun shownController(height: Dp = TALL_EDGE): EditorController {
        val controller = controller()
        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            Box(Modifier.requiredSize(WIDE_EDGE, height).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(WIDE_EDGE, height))
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { controller.callbacks.onSelectPaletteEntry(index(1)) }
        return controller
    }

    private fun requiredBackDispatcher(): OnBackPressedDispatcher =
        backDispatcher ?: error("The test activity provides no OnBackPressedDispatcher")

    private fun controller(): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val palette = Palette.create(listOf(opaque(CHANNEL_MAX, 0), opaque(0, CHANNEL_MAX))).requiredValue()
        val definition = PaletteDefinition.create(palette, index(0)).requiredValue()
        return EditorController.create(EditorRuntime.create(size, definition, FixedLayerDocumentIdSource))
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

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun stateDescription(text: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun paneTitle(text: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, text)

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid layer fixture: $rejection")
        }

    private companion object {
        val WIDE_EDGE: Dp = 600.dp
        val TALL_EDGE: Dp = 400.dp
        val PROBE_INSET: Dp = 4.dp

        /**
         * With 76-90dp of header and status the work area is 140-154dp: above the 104dp the Left limit needs, and
         * short enough that the Right panel (about 60dp) ends less than [LEFT_CLEARANCE] above the area's bottom.
         */
        val SHORT_EDGE: Dp = 230.dp
        val MARGIN: Dp = 16.dp
        val PANEL_MARGIN: Dp = 8.dp

        /** The panel's outer margin plus the quick-select clearance: 8dp + 56dp + 16dp + 16dp (#144 U3r ruling 3). */
        val LEFT_CLEARANCE: Dp = 96.dp
        val TOLERANCE: Dp = 1.dp
        const val CHIP_TAG: String = "editor_layer_chip"
        const val PANEL_TAG: String = "editor_layer_panel"
        const val CLOSE_TAG: String = "editor_layer_panel_close"
        const val CANVAS_TAG: String = "editor_canvas_64_48"
        const val DOCUMENT_WIDTH: Int = 64
        const val DOCUMENT_HEIGHT: Int = 48
        const val MAX_LAYERS: Int = 16
        const val CHANNEL_MAX: Int = 255
        const val SKY: String = "Sky"
    }
}

private object FixedLayerDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("4".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid layer fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
