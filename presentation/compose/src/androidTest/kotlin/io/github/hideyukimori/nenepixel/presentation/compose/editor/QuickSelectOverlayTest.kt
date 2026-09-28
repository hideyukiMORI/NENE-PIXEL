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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** The quick-select control's drag and tap modes and its placement over the work area (ADR 0029, #108 S2). */
internal class QuickSelectOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backDispatcher: OnBackPressedDispatcher? = null

    @Test
    fun draggingToARecentSlotAndReleasingSelectsItAndClosesTheFan() {
        val controller = paintedController()
        pressControl()
        composeRule.onNodeWithTag(FAN_TAG).assertExists()
        releaseOver(slotTag(1))
        assertEquals(index(1), controller.renderState.activePaletteIndex)
        assertNull(controller.renderState.quickSelection.menu)
        composeRule.onNodeWithTag(FAN_TAG).assertDoesNotExist()
    }

    @Test
    fun returningToTheControlBeforeReleasingLeavesTheSelection() {
        val controller = paintedController()
        pressControl()
        val item = offsetFromControl(slotTag(1))
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput {
            moveTo(item)
            moveTo(center)
        }
        composeRule.waitForIdle()
        assertNull(highlighted(controller))
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(index(0), controller.renderState.activePaletteIndex)
        composeRule.onNodeWithTag(FAN_TAG).assertDoesNotExist()
    }

    @Test
    fun theEyedropperItemArmsAndTheNextCanvasTapPicksTheSlotWithoutDrawing() {
        val controller = paintedController()
        pressControl()
        releaseOver(EYEDROPPER_TAG)
        assertEquals(EyedropperState.Armed, controller.renderState.quickSelection.eyedropper)
        composeRule.onNodeWithTag(CONTROL_TAG).assert(stateDescription(R.string.quick_select_state_eyedropper))
        val snapshot = controller.renderState.snapshot
        val canUndo = controller.renderState.canUndo
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(index(2), controller.renderState.activePaletteIndex)
        assertEquals(snapshot, controller.renderState.snapshot)
        assertEquals(canUndo, controller.renderState.canUndo)
        composeRule.onNodeWithTag(CONTROL_TAG).assert(stateDescription(R.string.quick_select_state_slot, 3))
    }

    @Test
    fun aCancelledPointerStreamCancelsTheMenu() {
        val controller = paintedController()
        pressControl()
        val item = offsetFromControl(slotTag(1))
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { moveTo(item) }
        composeRule.waitForIdle()
        assertEquals(
            "The slot must be highlighted, so a confirm would select it",
            QuickSelectItem.PaletteSlot(index(1)),
            highlighted(controller),
        )
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertEquals(index(0), controller.renderState.activePaletteIndex)
        assertNull(controller.renderState.quickSelection.menu)
    }

    @Test
    fun aShortTapKeepsTheMenuOpenWithoutAHighlightAndASecondTapCancelsIt() {
        val controller = paintedController()
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        val menu = controller.renderState.quickSelection.menu
        assertNotNull("A tap keeps the menu open in tap mode", menu)
        assertNull(menu?.highlighted)
        composeRule.onNodeWithTag(FAN_TAG).assertExists()
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertNull(controller.renderState.quickSelection.menu)
        assertEquals(index(0), controller.renderState.activePaletteIndex)
    }

    @Test
    fun theControlSitsInTheBottomCornerOnTheControlEdgeSide() {
        val controller = controller()
        setEditorContent(controller)
        EditorControlEdge.entries.forEach { edge ->
            composeRule.runOnIdle {
                controller.callbacks.onSetAppearance(controller.renderState.appearance.copy(controlEdge = edge))
            }
            val area = composeRule.onNodeWithTag(CANVAS_TAG).getUnclippedBoundsInRoot()
            val control = composeRule.onNodeWithTag(CONTROL_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertNear("$edge bottom margin", MARGIN, area.bottom - control.bottom)
            assertNear("$edge width", CONTROL_SIZE, control.right - control.left)
            when (edge) {
                EditorControlEdge.Left -> assertNear("Left margin", MARGIN, control.left - area.left)
                EditorControlEdge.Right -> assertNear("Right margin", MARGIN, area.right - control.right)
            }
        }
    }

    @Test
    fun tappingASlotItemInTapModeSelectsItAndClosesTheFanAndScrim() {
        val controller = paintedController()
        enterTapMode()
        composeRule.onNodeWithTag(SCRIM_TAG).assertExists()
        composeRule.onNodeWithTag(slotTag(1)).assert(hasClickAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(index(1), controller.renderState.activePaletteIndex)
        assertNull(controller.renderState.quickSelection.menu)
        composeRule.onNodeWithTag(FAN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingTheScrimCancelsWithoutReachingTheCanvas() {
        val controller = paintedController()
        enterTapMode()
        val snapshot = controller.renderState.snapshot
        val canUndo = controller.renderState.canUndo
        composeRule.onNodeWithTag(SCRIM_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(index(0), controller.renderState.activePaletteIndex)
        assertNull(controller.renderState.quickSelection.menu)
        assertEquals(snapshot, controller.renderState.snapshot)
        assertEquals(canUndo, controller.renderState.canUndo)
        composeRule.onNodeWithTag(FAN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun theAccessibilityClickOpensTapModeAndFocusesTheFirstItem() {
        val controller = paintedController()
        composeRule.onNodeWithTag(CONTROL_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val menu = controller.renderState.quickSelection.menu
        assertNotNull("The accessibility click opens the menu", menu)
        composeRule.onNodeWithTag(SCRIM_TAG).assertExists()
        val first = menu?.items?.first() ?: error("The open menu has no items")
        composeRule.onNodeWithTag(QuickSelectSemantics.itemTag(first)).assertIsFocused()
        composeRule.onNodeWithTag(CONTROL_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertNull(controller.renderState.quickSelection.menu)
    }

    @Test
    fun clickingTheControlWhileArmedDisarms() {
        val controller = paintedController()
        pressControl()
        releaseOver(EYEDROPPER_TAG)
        composeRule.onNodeWithTag(CONTROL_TAG).assert(stateDescription(R.string.quick_select_state_eyedropper))
        composeRule.onNodeWithTag(CONTROL_TAG).performClick()
        composeRule.waitForIdle()
        assertNotEquals(EyedropperState.Armed, controller.renderState.quickSelection.eyedropper)
        assertNull(controller.renderState.quickSelection.menu)
        composeRule.onNodeWithTag(CONTROL_TAG).assert(stateDescription(R.string.quick_select_state_slot, 1))
    }

    @Test
    fun backInTapModeCancelsAndLeavesTheSelection() {
        val controller = paintedController()
        enterTapMode()
        composeRule.onNodeWithTag(SCRIM_TAG).assertExists()
        composeRule.runOnIdle { requiredBackDispatcher().onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(index(0), controller.renderState.activePaletteIndex)
        assertNull(controller.renderState.quickSelection.menu)
        composeRule.onNodeWithTag(FAN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun backIsNotTakenWhileTheMenuIsClosedOrInDragMode() {
        paintedController()
        composeRule.runOnIdle { assertFalse(requiredBackDispatcher().hasEnabledCallbacks()) }
        pressControl()
        composeRule.onNodeWithTag(FAN_TAG).assertExists()
        composeRule.runOnIdle { assertFalse(requiredBackDispatcher().hasEnabledCallbacks()) }
    }

    private fun highlighted(controller: EditorController): QuickSelectItem? {
        val menu = controller.renderState.quickSelection.menu
        return menu?.highlighted
    }

    private fun enterTapMode() {
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FAN_TAG).assertExists()
    }

    private fun pressControl() {
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()
    }

    private fun releaseOver(tag: String) {
        val item = offsetFromControl(tag)
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput {
            moveTo(item)
            up()
        }
        composeRule.waitForIdle()
    }

    /** The centre of the item tagged [tag], in the control's own touch coordinates. */
    private fun offsetFromControl(tag: String): Offset {
        val control = composeRule.onNodeWithTag(CONTROL_TAG).getUnclippedBoundsInRoot()
        val item = composeRule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Offset(
                ((item.left + item.right) / 2 - control.left).toPx(),
                ((item.top + item.bottom) / 2 - control.top).toPx(),
            )
        }
    }

    /** Paints slot 1 then slot 2 at the canvas centre, so recent is [2, 1], then selects slot 0. */
    private fun paintedController(): EditorController {
        val controller = controller()
        setEditorContent(controller)
        listOf(index(1), index(2)).forEach { slot ->
            composeRule.runOnIdle { controller.callbacks.onSelectPaletteEntry(slot) }
            composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(center) }
            composeRule.waitForIdle()
        }
        assertEquals(listOf(index(2), index(1)), controller.renderState.quickSelection.recent)
        composeRule.runOnIdle { controller.callbacks.onSelectPaletteEntry(index(0)) }
        return controller
    }

    private fun stateDescription(
        resource: Int,
        vararg arguments: Any,
    ): SemanticsMatcher {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)
        return SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
    }

    private fun assertNear(
        message: String,
        expected: Dp,
        actual: Dp,
    ) {
        assertTrue("$message: expected $expected, was $actual", abs((expected - actual).value) <= TOLERANCE_DP)
    }

    private fun setEditorContent(controller: EditorController) {
        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            Box(Modifier.requiredSize(WIDE_EDGE, TALL_EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(WIDE_EDGE, TALL_EDGE))
            }
        }
    }

    private fun requiredBackDispatcher(): OnBackPressedDispatcher =
        backDispatcher ?: error("The test activity provides no OnBackPressedDispatcher")

    private fun controller(): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val palette =
            Palette
                .create(listOf(opaque(CHANNEL_MAX, 0, 0), opaque(0, CHANNEL_MAX, 0), opaque(0, 0, CHANNEL_MAX)))
                .requiredValue()
        val definition = PaletteDefinition.create(palette, index(0)).requiredValue()
        return EditorController.create(EditorRuntime.create(size, definition, FixedQuickSelectDocumentIdSource))
    }

    private fun opaque(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(CHANNEL_MAX).requiredValue(),
        )

    private fun slotTag(index: Int): String = "editor_quick_select_slot_${index + 1}"

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid quick-select fixture: $rejection")
        }

    private companion object {
        val WIDE_EDGE: Dp = 600.dp
        val TALL_EDGE: Dp = 400.dp
        val MARGIN: Dp = 16.dp
        val CONTROL_SIZE: Dp = 56.dp
        const val TOLERANCE_DP: Float = 1f
        const val CONTROL_TAG: String = "editor_quick_select"
        const val FAN_TAG: String = "editor_quick_select_fan"
        const val SCRIM_TAG: String = "editor_quick_select_scrim"
        const val EYEDROPPER_TAG: String = "editor_quick_select_eyedropper"
        const val CANVAS_TAG: String = "editor_canvas_4_3"
        const val DOCUMENT_WIDTH: Int = 4
        const val DOCUMENT_HEIGHT: Int = 3
        const val CHANNEL_MAX: Int = 255
    }
}

private object FixedQuickSelectDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("3".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid quick-select fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
