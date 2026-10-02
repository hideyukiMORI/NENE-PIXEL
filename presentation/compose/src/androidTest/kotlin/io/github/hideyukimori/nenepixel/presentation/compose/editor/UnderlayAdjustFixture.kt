package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayPlacement
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues

/**
 * An editor with a placed underlay, for the #171 adjust-mode tests: entering the mode from the underlay row's menu,
 * waiting for the adjust bar, and pressing Back. The editor is 600dp square, so the layout is the same in either
 * device orientation; gestures take their positions from the canvas node's bounds.
 */
internal object UnderlayAdjustFixture {
    const val BAR_TAG: String = "editor_underlay_adjust_bar"
    const val FIT_TAG: String = "editor_underlay_adjust_fit"
    const val DONE_TAG: String = "editor_underlay_adjust_done"
    const val OPACITY_TAG: String = "editor_underlay_adjust_opacity"
    const val ADJUST_TAG: String = "editor_underlay_adjust"
    const val MORE_TAG: String = "editor_underlay_more"
    const val CHIP_TAG: String = "editor_layer_chip"
    const val CLOSE_TAG: String = "editor_layer_panel_close"
    const val TIMEOUT_MILLIS: Long = 5_000

    /**
     * Shows a 64 x 48 editor with an 8 x 4 image placed as the underlay, resting and shown. [onBackDispatcher]
     * receives the test activity's back dispatcher.
     */
    fun show(
        rule: ComposeContentTestRule,
        onBackDispatcher: (OnBackPressedDispatcher?) -> Unit = {},
    ): EditorController {
        val controller = PresentationTestValues.fixture(PresentationTestValues.canvas(WIDTH, HEIGHT)).controller
        val port = ReferenceImagePort { ReferenceImageOutcome.Picked(image) }
        rule.setContent {
            onBackDispatcher(LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher)
            Box(Modifier.requiredSize(EDGE, EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(EDGE, EDGE), referenceImage = port)
            }
        }
        rule.waitForIdle()
        val underlay = ReferenceUnderlay.placed(image, controller.renderState.document.size)
        rule.runOnIdle { controller.callbacks.underlay.onSet(underlay) }
        rule.waitUntil(TIMEOUT_MILLIS) { controller.renderState.underlay != null }
        return controller
    }

    /** Opens the panel and the underlay row's menu, chooses "Move and scale" and waits for the adjust bar. */
    fun enterAdjust(rule: ComposeContentTestRule) {
        LayerMenuFixture.openPanel(rule)
        LayerMenuFixture.click(rule, MORE_TAG)
        LayerMenuFixture.click(rule, ADJUST_TAG)
        waitForBar(rule, shown = true)
    }

    /** Waits until the adjust bar is in the tree when [shown], or gone when not. */
    fun waitForBar(
        rule: ComposeContentTestRule,
        shown: Boolean,
    ) {
        rule.waitUntil(TIMEOUT_MILLIS) { exists(rule, BAR_TAG) == shown }
    }

    fun exists(
        rule: ComposeContentTestRule,
        tag: String,
    ): Boolean = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    fun underlay(controller: EditorController): ReferenceUnderlay = requireNotNull(controller.renderState.underlay)

    fun fittedPlacement(controller: EditorController): UnderlayPlacement =
        UnderlayPlacement.fitted(image, controller.renderState.document.size)

    fun canvas(rule: ComposeContentTestRule): SemanticsNodeInteraction = rule.onNodeWithTag(CANVAS_TAG)

    /** Presses Back through [dispatcher] and waits for the tree to settle. */
    fun pressBack(
        rule: ComposeContentTestRule,
        dispatcher: OnBackPressedDispatcher?,
    ) {
        val back = dispatcher ?: error("The test activity provides no OnBackPressedDispatcher")
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
    }

    private val image = UnderlayDisplayFixture.greenImage(IMAGE_WIDTH, IMAGE_HEIGHT)
    private val EDGE: Dp = 600.dp
    private const val CANVAS_TAG: String = "editor_canvas_64_48"
    private const val WIDTH: Int = 64
    private const val HEIGHT: Int = 48
    private const val IMAGE_WIDTH: Int = 8
    private const val IMAGE_HEIGHT: Int = 4
}
