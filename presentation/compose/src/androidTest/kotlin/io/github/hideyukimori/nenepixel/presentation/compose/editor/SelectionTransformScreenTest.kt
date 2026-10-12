package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.CANVAS_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.SELECT_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SelectionScreenFixture.pixel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** #213: transform journey, bounded-control refusal and short-surface footer/scroll acceptance. */
internal class SelectionTransformScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun flipsThenQuarterTurnTranslateAndApplyKeepExactCellsPreviewAndOneUndoRedo() {
        val controller = SelectionScreenFixture.show(rule)
        paint(controller, pixel(3, 0), 0)
        paint(controller, pixel(4, 0), 1)
        paint(controller, pixel(4, 1), 0)
        paint(controller, pixel(5, 1), 1)
        val before = controller.documentState
        begin(controller)
        drag(controller, pixel(3, 0), pixel(5, 1))
        LayerMenuFixture.click(rule, HORIZONTAL_TAG)
        LayerMenuFixture.click(rule, VERTICAL_TAG)
        LayerMenuFixture.click(rule, CLOCKWISE_TAG)

        assertEquals(
            PixelOrientation.RotateCounterClockwise,
            controller.renderState.selectionSession
                ?.previewTransfer
                ?.orientation,
        )
        rule.onNodeWithTag(STATUS_TAG).assertTextEquals(text(R.string.selection_size, 2, 3))
        rule.onNodeWithTag(APPLY_TAG).assertIsEnabled()
        assertSame(before, controller.documentState)
        drag(controller, pixel(3, 0), pixel(4, 0))
        val preview = SelectionScreenFixture.samples(rule, controller)
        LayerMenuFixture.click(rule, APPLY_TAG)

        val applied = controller.documentState
        assertNull(controller.renderState.selectionSession)
        assertEquals(before.revision.value + 1L, applied.revision.value)
        assertEquals(preview, SelectionScreenFixture.samples(rule, controller))
        assertCells(controller, listOf(null, 1, 1, 0, 0, null))
        rule.onNodeWithTag("editor_undo").performClick()
        assertLayers(before, controller)
        rule.onNodeWithTag("editor_redo").performClick()
        assertLayers(applied, controller)
    }

    @Test
    fun transformsAreDisabledBeforeAndDuringSelectionAndImpossibleRotationExplainsWhy() {
        val controller = SelectionScreenFixture.show(rule)
        val before = controller.documentState
        begin(controller)
        listOf(HORIZONTAL_TAG, VERTICAL_TAG, CLOCKWISE_TAG).forEach {
            rule.onNodeWithTag(it).assertIsNotEnabled()
        }
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(offset(controller, pixel(0, 1)))
            moveTo(offset(controller, pixel(6, 1)))
        }
        rule.waitForIdle()
        listOf(HORIZONTAL_TAG, VERTICAL_TAG, CLOCKWISE_TAG).forEach {
            rule.onNodeWithTag(it).assertIsNotEnabled()
        }
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput { up() }
        rule.waitForIdle()

        rule.onNodeWithTag(HORIZONTAL_TAG).assertIsEnabled()
        rule.onNodeWithTag(VERTICAL_TAG).assertIsEnabled()
        rule.onNodeWithTag(CLOCKWISE_TAG).assertIsNotEnabled()
        rule
            .onNodeWithTag(UNAVAILABLE_TAG)
            .assertIsDisplayed()
            .assertTextEquals(text(R.string.selection_rotation_outside_canvas))
        rule.onNodeWithTag(STATUS_TAG).assertTextEquals(text(R.string.selection_size, 7, 1))
        val previous = controller.renderState.selectionSession
        rule.onNodeWithTag(CLOCKWISE_TAG).performTouchInput { click(center) }
        assertSame(previous, controller.renderState.selectionSession)
        assertSame(before, controller.documentState)
        LayerMenuFixture.click(rule, CANCEL_TAG)
        assertNull(controller.renderState.selectionSession)
        assertSame(before, controller.documentState)
    }

    @Test
    fun shortNarrowSurfaceScrollsTransformsWhileApplyAndCancelStayReachable() {
        val controller = showShortEditor()
        val before = controller.documentState
        rule
            .onNodeWithTag(CLOCKWISE_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule
            .onNodeWithTag(APPLY_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performTouchInput { click(center) }
        assertNull(controller.renderState.selectionSession)
        // The blank transformed rectangle is a genuine command no-op, but the Apply intent still closes its draft.
        assertSame(before, controller.documentState)
        rule.runOnIdle {
            controller.callbacks.selection.onBegin(
                before.frames
                    .single()
                    .layers
                    .single()
                    .id,
            )
        }
        rule.onNodeWithTag(CANCEL_TAG).assertIsDisplayed().performTouchInput { click(center) }
        assertNull(controller.renderState.selectionSession)
        assertSame(before, controller.documentState)
    }

    /** Prepares only the existing canonical workspace geometry; no normal application document or save data. */
    private fun showShortEditor(): EditorController {
        val controller = PresentationTestValues.fixture(PresentationTestValues.canvas(8, 6)).controller
        controller.callbacks.selection.onBegin(
            controller.documentState.frames
                .single()
                .layers
                .single()
                .id,
        )
        controller.runtime.reduce(WorkspaceAction.BeginSelectionPointer(pixel(3, 0)))
        controller.runtime.reduce(WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(5, 1)))
        controller.runtime.reduce(WorkspaceAction.FinishSelectionPointer)
        controller.synchronizeWithRuntime()
        rule.setContent {
            Box(Modifier.requiredSize(SHORT_WIDTH, SHORT_HEIGHT).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(SHORT_WIDTH, SHORT_HEIGHT))
            }
        }
        rule.waitForIdle()
        return controller
    }

    private fun begin(controller: EditorController) {
        LayerMenuFixture.choose(
            rule,
            controller.documentState.frames
                .single()
                .layers
                .single()
                .id,
            SELECT_TAG,
        )
        rule.onNodeWithTag(SelectionScreenFixture.BAR_TAG).assertIsDisplayed()
    }

    private fun paint(
        controller: EditorController,
        pixel: PixelPosition,
        slot: Int,
    ) {
        rule.runOnIdle { controller.callbacks.onSelectPaletteEntry(QuickSelectFixture.index(slot)) }
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(offset(controller, pixel)) }
        rule.waitForIdle()
    }

    private fun drag(
        controller: EditorController,
        from: PixelPosition,
        to: PixelPosition,
    ) {
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(offset(controller, from))
            moveTo(offset(controller, to))
            up()
        }
        rule.waitForIdle()
    }

    private fun TouchInjectionScope.offset(
        controller: EditorController,
        pixel: PixelPosition,
    ): Offset = SelectionScreenFixture.offset(this, rule, controller, pixel)

    /** Independent 2 by 3 destination oracle: Empty/G, G/R, R/Empty. */
    private fun assertLayers(
        expected: DocumentState,
        controller: EditorController,
    ) {
        assertEquals(
            expected.frames.single().layers,
            controller.documentState.frames
                .single()
                .layers,
        )
    }

    private fun assertCells(
        controller: EditorController,
        expected: List<Int?>,
    ) {
        val snapshot =
            controller.documentState.frames
                .single()
                .layers
                .single()
                .snapshot
        expected.forEachIndexed { index, slot ->
            val cell =
                when (val result = snapshot.cellAt(pixel(4 + index % 2, index / 2))) {
                    is DomainValueResult.Created -> result.value
                    is DomainValueResult.Rejected -> error("Invalid native transform sample: ${result.rejection}")
                }
            val wanted = if (slot == null) PixelCell.Empty else PixelCell.Covered(QuickSelectFixture.index(slot))
            assertEquals(wanted, cell)
        }
    }

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private companion object {
        val SHORT_WIDTH = 320.dp
        val SHORT_HEIGHT = 420.dp
        const val HORIZONTAL_TAG = "editor_selection_flip_horizontal"
        const val VERTICAL_TAG = "editor_selection_flip_vertical"
        const val CLOCKWISE_TAG = "editor_selection_rotate_clockwise"
        const val UNAVAILABLE_TAG = "editor_selection_rotation_unavailable"
        const val STATUS_TAG = "editor_selection_status"
        const val APPLY_TAG = "editor_selection_apply"
        const val CANCEL_TAG = "editor_selection_cancel"
    }
}
