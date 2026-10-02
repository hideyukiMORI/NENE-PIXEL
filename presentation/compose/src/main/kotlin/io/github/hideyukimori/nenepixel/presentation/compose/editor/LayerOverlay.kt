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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * The layer chip, or the open layer panel in the same corner, over the work area (#144 UI spec "Layout", U3r).
 * The corner is always the physical top left; the control edge only lowers the panel's height limit.
 *
 * It reads only the row list, the active layer, the control edge, the layer notice and the reference underlay
 * through one `derivedStateOf`; a stroke in progress or a committed stroke leaves those structurally equal, so
 * neither the chip nor the panel recomposes. Open or closed is local, saveable Compose state (ADR 0020), not
 * workspace state. Back closes the panel, and closing returns focus to the chip. The overlay takes no pointer
 * itself: only the chip, the panel and a shown notice do. The panel rows and the add row send their changes
 * through the layer route of [editorCallbacks], passed on as the same instance inside one remembered
 * [LayerRowActions]. A row's rename opens the rename dialog (U7) for that layer; the target is plain `remember`
 * state, and the dialog is composed only while it is open. The layer notice (U6) shows at the bottom centre
 * whether the panel is open or not; its undo goes through [editorCallbacks] like the dock's. After the last row,
 * inside the rows' scroll, sits the underlay row (#170), which sends its changes through the underlay route of
 * [editorCallbacks] and calls [onPickUnderlay] to choose or replace the image; its "move and scale" closes the
 * panel like the panel's own close (#171).
 */
@Composable
internal fun LayerOverlay(
    state: State<EditorRenderState>,
    editorCallbacks: EditorCallbacks,
    onPickUnderlay: () -> Unit,
) {
    val inputs by rememberOverlayInputs(state)
    val callbacks = editorCallbacks.layers
    var open by rememberSaveable { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val chipFocus = remember { FocusRequester() }
    val renameTarget = remember { mutableStateOf<LayerId?>(null) }
    val actions = remember(callbacks) { LayerRowActions(callbacks, onRename = { id -> renameTarget.value = id }) }
    val onAdd = remember<() -> Unit>(callbacks) { { callbacks.onAdd() } }
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
        AnimatedVisibility(open, Modifier.align(corner), panelEnter(density), PANEL_EXIT) {
            LayerPanel(inputs.rows.size, close, Modifier.panelBounds(maxWidth, maxHeight, inputs.edge)) {
                LayerPanelRows(inputs.rows, inputs.activeLayerId, actions) {
                    LayerPanelUnderlayRow(inputs.underlay, editorCallbacks.underlay, onPickUnderlay, close)
                }
                LayerPanelAddRow(inputs.rows.size, onAdd)
            }
        }
        LayerNoticeHost(inputs.notice, editorCallbacks, Modifier.align(Alignment.BottomCenter).noticeBounds())
    }
    LayerRenameHost(renameTarget, inputs.rows, callbacks)
}

/** The panel margin, the width cap for a work area [workWidth] wide, and the height limit (#144 "パネル"). */
private fun Modifier.panelBounds(
    workWidth: Dp,
    workHeight: Dp,
    edge: EditorControlEdge,
): Modifier =
    padding(LayerGeometry.PANEL_MARGIN)
        .width(min(LayerGeometry.PANEL_WIDTH, (workWidth - LayerGeometry.PANEL_MARGIN * 2).coerceAtLeast(0.dp)))
        .heightIn(max = LayerGeometry.panelMaxHeight(workHeight, edge))

/** The overlay's inputs, read from [state] through one `derivedStateOf`. */
@Composable
private fun rememberOverlayInputs(state: State<EditorRenderState>): State<LayerOverlayInputs> =
    remember(state) {
        derivedStateOf {
            val render = state.value
            LayerOverlayInputs(
                layerRowsOf(render.document),
                render.activeLayerId,
                render.appearance.controlEdge,
                render.layerNotice,
                render.underlay,
            )
        }
    }

/** The notice's place: 16dp above the bottom, clear of the quick-select control, at most 360dp wide (#144). */
private fun Modifier.noticeBounds(): Modifier =
    padding(
        start = LayerGeometry.NOTICE_SIDE_MARGIN,
        end = LayerGeometry.NOTICE_SIDE_MARGIN,
        bottom = LayerGeometry.MARGIN,
    ).widthIn(max = LayerGeometry.NOTICE_MAX_WIDTH)

private fun panelEnter(density: Density): EnterTransition {
    val offset = with(density) { LayerGeometry.PANEL_ENTRY_OFFSET.roundToPx() } * LayerGeometry.ENTRY_SIGN
    return slideInHorizontally(tween(PANEL_ENTER_MILLIS, easing = FastOutSlowInEasing)) { offset } +
        fadeIn(tween(PANEL_ENTER_MILLIS, easing = FastOutSlowInEasing))
}

private val PANEL_EXIT: ExitTransition = fadeOut(tween(PANEL_EXIT_MILLIS, easing = LinearOutSlowInEasing))
private const val PANEL_ENTER_MILLIS: Int = 150
private const val PANEL_EXIT_MILLIS: Int = 100

/**
 * What the chip, the panel and the notice read; structurally equal across strokes because the rows never hold a
 * snapshot and a stroke leaves the notice and the underlay as they were.
 */
private data class LayerOverlayInputs(
    val rows: List<LayerRowModel>,
    val activeLayerId: LayerId,
    val edge: EditorControlEdge,
    val notice: LayerNotice?,
    val underlay: ReferenceUnderlay?,
) {
    val active: LayerRowModel
        get() =
            rows.firstOrNull { row -> row.id == activeLayerId }
                ?: error("Active layer ${activeLayerId.value} is not among rows ${rows.map { row -> row.id.value }}")
}
