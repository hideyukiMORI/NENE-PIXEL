package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge

/** The layer chip's and panel's sizes and their corner of the work area (#144 UI spec "Layout"). */
internal object LayerGeometry {
    val MARGIN: Dp = 16.dp

    /** The chip looks 40dp tall inside a 48dp touch box, so 12dp puts its visible edge 16dp from the top. */
    val CHIP_MARGINS: PaddingValues = PaddingValues(start = MARGIN, top = 12.dp, end = MARGIN)
    val PANEL_MARGIN: Dp = 8.dp
    val PANEL_WIDTH: Dp = 280.dp
    val PANEL_CORNER: Dp = 8.dp
    val PANEL_PADDING: Dp = 8.dp
    val PANEL_TOP_PADDING: Dp = 4.dp
    val PANEL_SPACING: Dp = 4.dp
    val PANEL_ENTRY_OFFSET: Dp = 24.dp
    val HEADER_HEIGHT: Dp = 48.dp
    val CHIP_MIN_HEIGHT: Dp = 40.dp
    val CHIP_MIN_WIDTH: Dp = 96.dp
    val CHIP_MAX_WIDTH: Dp = 220.dp
    val CHIP_PADDING: Dp = 12.dp
    val CHIP_CORNER: Dp = 6.dp
    val SYMBOL_SIZE: Dp = 20.dp
    val GAP: Dp = 8.dp
    val BORDER: Dp = 1.dp

    /** Room below the panel for the bottom-left quick-select control: 56dp control + 16dp margin + 16dp gap. */
    val QUICK_SELECT_CLEARANCE: Dp = 88.dp

    /**
     * The chip's and the panel's corner is always the physical top left, whatever the control edge: the actual-size
     * window starts at the physical top right, so the two never meet (#144 U3r ruling 1). A right-to-left layout does
     * not mirror it.
     */
    val CORNER: Alignment = AbsoluteAlignment.TopLeft

    /** The panel enters sideways from the left, the side of its corner (#144 U3r ruling 2). */
    const val ENTRY_SIGN: Int = -1

    /**
     * The tallest the panel may be inside a work area [workHeight] tall. With the control edge on the left the
     * quick-select control sits bottom left, under the panel's corner, so the panel stops [QUICK_SELECT_CLEARANCE]
     * above the usual limit (#144 U3r ruling 3).
     */
    fun panelMaxHeight(
        workHeight: Dp,
        edge: EditorControlEdge,
    ): Dp {
        val clearance =
            when (edge) {
                EditorControlEdge.Left -> QUICK_SELECT_CLEARANCE
                EditorControlEdge.Right -> 0.dp
            }
        return (workHeight - PANEL_MARGIN * 2 - clearance).coerceAtLeast(0.dp)
    }
}
