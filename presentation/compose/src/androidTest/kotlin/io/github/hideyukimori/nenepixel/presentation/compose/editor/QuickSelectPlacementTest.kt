package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CANVAS_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CONTROL_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.DOCK_TAGS
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.FAN_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.SLOT_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The control and its largest fan stay inside the work area and clear of the tool dock in both layouts and on both
 * control edges (ADR 0029 "Floating control", UI spec "Responsive Behavior"). The editor fills the test window, so
 * the checks hold in either device orientation.
 */
internal class QuickSelectPlacementTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabletopLeft() = assertPlacement(EditorLayout.Tabletop, EditorControlEdge.Left)

    @Test
    fun tabletopRight() = assertPlacement(EditorLayout.Tabletop, EditorControlEdge.Right)

    @Test
    fun handheldLeft() = assertPlacement(EditorLayout.Handheld, EditorControlEdge.Left)

    @Test
    fun handheldRight() = assertPlacement(EditorLayout.Handheld, EditorControlEdge.Right)

    private fun assertPlacement(
        layout: EditorLayout,
        edge: EditorControlEdge,
    ) {
        val controller = QuickSelectFixture.controller()
        QuickSelectFixture.show(composeRule, controller)
        QuickSelectFixture.paint(composeRule, controller, (0 until SLOT_COUNT).toList())
        composeRule.runOnIdle {
            val appearance = controller.renderState.appearance
            controller.callbacks.onSetAppearance(appearance.copy(layout = layout, controlEdge = edge))
        }
        composeRule.waitForIdle()
        val name = "$layout/$edge"
        val area = bounds(CANVAS_TAG)
        val control = bounds(CONTROL_TAG)
        assertControl(name, edge, area, control)
        assertFan(name, controller, area)
    }

    private fun assertControl(
        name: String,
        edge: EditorControlEdge,
        area: DpRect,
        control: DpRect,
    ) {
        assertContained("$name control", area, control)
        DOCK_TAGS.forEach { tag -> assertFalse("$name control overlaps $tag", overlaps(control, bounds(tag))) }
        val bottom = area.bottom - control.bottom
        val side =
            when (edge) {
                EditorControlEdge.Left -> control.left - area.left
                EditorControlEdge.Right -> area.right - control.right
            }
        Log.i(LOG_TAG, "$name area=$area control=$control bottom=$bottom side=$side")
        assertNear("$name bottom margin", MARGIN, bottom)
        assertNear("$name side margin", MARGIN, side)
    }

    private fun assertFan(
        name: String,
        controller: EditorController,
        area: DpRect,
    ) {
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FAN_TAG).assertIsDisplayed()
        val menu = controller.renderState.quickSelection.menu
        val items = menu?.items.orEmpty()
        assertEquals("$name fan items: recent 8 and the eyedropper", SLOT_COUNT + 1, items.size)
        items.forEach { item ->
            val tag = QuickSelectSemantics.itemTag(item)
            val itemBounds = bounds(tag)
            Log.i(LOG_TAG, "$name $tag=$itemBounds")
            assertContained("$name $tag", area, itemBounds)
        }
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertNull(controller.renderState.quickSelection.menu)
    }

    private fun bounds(tag: String): DpRect =
        composeRule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()

    private fun overlaps(
        first: DpRect,
        second: DpRect,
    ): Boolean =
        first.left < second.right && second.left < first.right && first.top < second.bottom && second.top < first.bottom

    private fun assertContained(
        message: String,
        outer: DpRect,
        inner: DpRect,
    ) {
        val tolerance = TOLERANCE_DP.dp
        assertTrue(
            "$message: $inner must be inside $outer",
            inner.left >= outer.left - tolerance &&
                inner.top >= outer.top - tolerance &&
                inner.right <= outer.right + tolerance &&
                inner.bottom <= outer.bottom + tolerance,
        )
    }

    private fun assertNear(
        message: String,
        expected: Dp,
        actual: Dp,
    ) {
        assertTrue("$message: expected $expected, was $actual", abs((expected - actual).value) <= TOLERANCE_DP)
    }

    private companion object {
        val MARGIN: Dp = 16.dp
        const val TOLERANCE_DP: Float = 1f
        const val LOG_TAG: String = "QuickSelectPlacement"
    }
}
