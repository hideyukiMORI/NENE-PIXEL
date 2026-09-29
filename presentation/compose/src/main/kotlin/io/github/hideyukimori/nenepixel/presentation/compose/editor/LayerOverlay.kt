package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * The layer chip, or the open layer panel in the same corner, over the work area (#144 UI spec "Layout", U3r).
 * The corner is always the physical top left; the control edge only lowers the panel's height limit.
 *
 * It reads only the row list, the active layer and the control edge through one `derivedStateOf`; a stroke in
 * progress or a committed stroke leaves those structurally equal, so neither the chip nor the panel recomposes.
 * Open or closed is local, saveable Compose state (ADR 0020), not workspace state. Back closes the panel, and
 * closing returns focus to the chip. The overlay takes no pointer itself: only the chip and the panel do.
 */
@Composable
internal fun LayerOverlay(state: State<EditorRenderState>) {
    val inputs by remember(state) {
        derivedStateOf {
            val render = state.value
            LayerOverlayInputs(layerRowsOf(render.document), render.activeLayerId, render.appearance.controlEdge)
        }
    }
    var open by rememberSaveable { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val chipFocus = remember { FocusRequester() }
    val close = {
        open = false
        returnFocus = true
    }
    BackHandler(enabled = open, onBack = close)
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            chipFocus.requestFocus()
            returnFocus = false
        }
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val corner = LayerGeometry.CORNER
        if (!open) {
            val chip = Modifier.align(corner).padding(LayerGeometry.CHIP_MARGINS).focusRequester(chipFocus)
            LayerChip(inputs.active, onClick = { open = true }, modifier = chip)
        }
        val margins = LayerGeometry.PANEL_MARGIN * 2
        AnimatedVisibility(open, Modifier.align(corner), panelEnter(density), PANEL_EXIT) {
            val panel =
                Modifier
                    .padding(LayerGeometry.PANEL_MARGIN)
                    .width(min(LayerGeometry.PANEL_WIDTH, (maxWidth - margins).coerceAtLeast(0.dp)))
                    .heightIn(max = LayerGeometry.panelMaxHeight(maxHeight, inputs.edge))
            LayerPanel(inputs.rows.size, close, panel)
        }
    }
}

private fun panelEnter(density: Density): EnterTransition {
    val offset = with(density) { LayerGeometry.PANEL_ENTRY_OFFSET.roundToPx() } * LayerGeometry.ENTRY_SIGN
    return slideInHorizontally(tween(PANEL_ENTER_MILLIS, easing = FastOutSlowInEasing)) { offset } +
        fadeIn(tween(PANEL_ENTER_MILLIS, easing = FastOutSlowInEasing))
}

private val PANEL_EXIT: ExitTransition = fadeOut(tween(PANEL_EXIT_MILLIS, easing = LinearOutSlowInEasing))
private const val PANEL_ENTER_MILLIS: Int = 150
private const val PANEL_EXIT_MILLIS: Int = 100

/** What the chip and the panel read; structurally equal across strokes because the rows never hold a snapshot. */
private data class LayerOverlayInputs(
    val rows: List<LayerRowModel>,
    val activeLayerId: LayerId,
    val edge: EditorControlEdge,
) {
    val active: LayerRowModel
        get() = rows.first { row -> row.id == activeLayerId }
}
