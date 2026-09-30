package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.ADD_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.LIMIT_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LayerMenuFixture.MAX_LAYERS
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CANVAS_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CONTROL_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The layer chip and the open layer panel sit at the physical top left of the work area in both layouts and on both
 * control edges, and stay clear of the quick-select control (#144 UI spec "Layout" and "Responsive Behavior", U3r
 * ruling 3). The document holds the full 16 layers, so the panel meets its height limit wherever the work area is
 * short enough, and the add row still shows below the scrolling rows. The editor fills the test window, so the checks
 * hold in either device orientation.
 */
internal class LayerPlacementTest {
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
        composeRule.runOnIdle {
            repeat(MAX_LAYERS - 1) { controller.callbacks.layers.onAdd() }
            val appearance = controller.renderState.appearance
            controller.callbacks.onSetAppearance(appearance.copy(layout = layout, controlEdge = edge))
        }
        composeRule.waitForIdle()
        assertEquals(MAX_LAYERS, controller.renderState.document.layers.size)
        val name = "$layout/$edge"
        val area = bounds(CANVAS_TAG)
        val control = bounds(CONTROL_TAG)
        assertChip(name, area, control)
        composeRule.onNodeWithTag(CHIP_TAG).performClick()
        composeRule.waitForIdle()
        assertPanel(name, edge, area, control)
    }

    private fun assertChip(
        name: String,
        area: DpRect,
        control: DpRect,
    ) {
        val chip = bounds(CHIP_TAG)
        Log.i(LOG_TAG, "$name area=$area chip=$chip control=$control")
        assertNear("$name chip left margin", CHIP_MARGIN, chip.left - area.left)
        assertNear("$name chip top margin", CHIP_MARGIN, chip.top - area.top)
        val middle = (area.top + area.bottom) / 2
        assertTrue("$name chip $chip must stay in the top half of $area", chip.bottom < middle)
        assertFalse("$name chip $chip overlaps the quick-select control $control", overlaps(chip, control))
    }

    private fun assertPanel(
        name: String,
        edge: EditorControlEdge,
        area: DpRect,
        control: DpRect,
    ) {
        val panel = bounds(PANEL_TAG)
        val add = bounds(ADD_TAG)
        val limit = bounds(LIMIT_TAG)
        Log.i(LOG_TAG, "$name panel=$panel add=$add limit=$limit")
        assertNear("$name panel left margin", PANEL_MARGIN, panel.left - area.left)
        assertNear("$name panel top margin", PANEL_MARGIN, panel.top - area.top)
        val width = min(PANEL_WIDTH, area.right - area.left - PANEL_MARGIN * 2)
        assertNear("$name panel width", width, panel.right - panel.left)
        val lowest =
            when (edge) {
                EditorControlEdge.Left -> area.bottom - PANEL_MARGIN - QUICK_SELECT_CLEARANCE
                EditorControlEdge.Right -> area.bottom - PANEL_MARGIN
            }
        assertTrue("$name panel $panel must end above $lowest", panel.bottom <= lowest + TOLERANCE)
        assertContained("$name add row", panel, add)
        assertContained("$name limit line", panel, limit)
        assertFalse("$name panel $panel overlaps the quick-select control $control", overlaps(panel, control))
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
        assertTrue(
            "$message: $inner must be inside $outer",
            inner.left >= outer.left - TOLERANCE &&
                inner.top >= outer.top - TOLERANCE &&
                inner.right <= outer.right + TOLERANCE &&
                inner.bottom <= outer.bottom + TOLERANCE,
        )
    }

    private fun assertNear(
        message: String,
        expected: Dp,
        actual: Dp,
    ) {
        assertTrue("$message: expected $expected, was $actual", abs((expected - actual).value) <= TOLERANCE.value)
    }

    private companion object {
        /** The chip's visible edge: 16dp from the top and the left of the work area (#144 "チップ"). */
        val CHIP_MARGIN: Dp = 16.dp
        val PANEL_MARGIN: Dp = 8.dp
        val PANEL_WIDTH: Dp = 280.dp

        /** Room for the bottom-left quick-select control: 56dp control + 16dp margin + 16dp gap (#144 U3r ruling 3). */
        val QUICK_SELECT_CLEARANCE: Dp = 88.dp
        val TOLERANCE: Dp = 1.dp
        const val CHIP_TAG: String = "editor_layer_chip"
        const val PANEL_TAG: String = "editor_layer_panel"
        const val LOG_TAG: String = "LayerPlacement"
    }
}
