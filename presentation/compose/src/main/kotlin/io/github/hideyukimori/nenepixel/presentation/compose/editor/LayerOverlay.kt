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
import androidx.compose.runtime.MutableState
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
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * The physical top-left layer chip opens Layers; its heading opens the secondary Frames pane.
 * Snapshot-free row metadata stays structurally equal across strokes. Pane, row More and dialogs
 * are local presentation mechanics; every frame/layer mutation uses the callbacks' runtime route.
 * Back closes a child popup/dialog first, then Frames returns to Layers, then Layers returns focus
 * to the chip. The bounded panel surfaces consume pointers above the shared canvas route.
 */
@Composable
internal fun LayerOverlay(
    state: State<EditorRenderState>,
    editorCallbacks: EditorCallbacks,
    onPickUnderlay: () -> Unit,
) {
    val inputs by rememberOverlayInputs(state)
    val pane = rememberSaveable { mutableStateOf<LayerPane?>(null) }
    var returnFocus by remember { mutableStateOf(false) }
    val chipFocus = remember { FocusRequester() }
    val renameTarget = remember { mutableStateOf<LayerId?>(null) }
    val close = {
        pane.value = null
        returnFocus = true
    }
    val presentation = LayerOverlayPresentation(pane, chipFocus, close)
    val actions = rememberLayerRowActions(editorCallbacks, renameTarget, close)
    BackHandler(enabled = pane.value != null, onBack = presentation::back)
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            chipFocus.requestFocus()
            returnFocus = false
        }
    }
    LayerOverlaySurface(inputs, presentation, LayerPaneActions(editorCallbacks, actions, onPickUnderlay))
    LayerRenameHost(renameTarget, inputs.layers.rows.rows, editorCallbacks.layers)
}

/** The surface owns only local pane/focus mechanics; editor frame/layer choice remains projected. */
@Composable
private fun LayerOverlaySurface(
    inputs: LayerOverlayInputs,
    presentation: LayerOverlayPresentation,
    actions: LayerPaneActions,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val corner = LayerGeometry.CORNER
        if (presentation.pane.value == null) {
            val chip =
                Modifier
                    .align(corner)
                    .padding(LayerGeometry.CHIP_MARGINS)
                    .focusRequester(presentation.chipFocus)
            LayerChip(inputs.layers.active, onClick = { presentation.pane.value = LayerPane.Layers }, modifier = chip)
        }
        AnimatedVisibility(presentation.pane.value != null, Modifier.align(corner), panelEnter(density), PANEL_EXIT) {
            LayerOverlayPane(inputs, presentation, actions, LayerPaneBounds(maxWidth, maxHeight, inputs.edge))
        }
        LayerNoticeHost(
            inputs.layers.notice,
            actions.callbacks,
            Modifier.align(Alignment.BottomCenter).noticeBounds(),
        )
    }
}

@Composable
private fun LayerOverlayPane(
    inputs: LayerOverlayInputs,
    presentation: LayerOverlayPresentation,
    actions: LayerPaneActions,
    bounds: LayerPaneBounds,
) {
    if (presentation.pane.value == LayerPane.Frames) {
        FramePanel(
            inputs.frames,
            frameActions(actions.callbacks.frames, presentation),
            bounds.modifier(LayerPane.Frames),
        )
    } else {
        val rows = inputs.layers.rows
        val heading =
            LayerPanelHeadingModel(
                rows.rows.size,
                inputs.frames.rows.indexOfFirst {
                    it.id == inputs.frames.active
                } + 1,
                inputs.frames.rows.size,
            )
        LayerPanel(
            heading,
            LayerPanelNavigation(presentation.close) { presentation.pane.value = LayerPane.Frames },
            bounds.modifier(LayerPane.Layers),
        ) {
            LayerPanelRows(rows, actions.rows) {
                LayerPanelUnderlayRow(
                    inputs.layers.underlay,
                    actions.callbacks.underlay,
                    actions.pickUnderlay,
                    presentation.close,
                )
            }
            LayerPanelAddRow(rows.totalLayerCount) { actions.callbacks.layers.onAdd() }
        }
    }
}

private fun frameActions(
    callbacks: EditorFrameCallbacks,
    presentation: LayerOverlayPresentation,
): FramePanelActions =
    FramePanelActions(callbacks, { id ->
        when (callbacks.onSelect(id)) {
            is WorkspaceReductionResult.Reduced, is WorkspaceReductionResult.Unchanged -> {
                presentation.pane.value = LayerPane.Layers
            }

            is WorkspaceReductionResult.Rejected, is WorkspaceReductionResult.CommitPrepared -> {
            }
        }
    }, presentation::back)

/** Row actions retain the row identity; only a successful canonical selection start closes the panel. */
@Composable
private fun rememberLayerRowActions(
    callbacks: EditorCallbacks,
    renameTarget: MutableState<LayerId?>,
    close: () -> Unit,
): LayerRowActions =
    remember(callbacks.layers, callbacks.selection, renameTarget) {
        LayerRowActions(
            callbacks.layers,
            onRename = { id -> renameTarget.value = id },
            onSelectArea = { id ->
                when (callbacks.selection.onBegin(id)) {
                    is WorkspaceReductionResult.Reduced, is WorkspaceReductionResult.Unchanged -> close()
                    is WorkspaceReductionResult.Rejected, is WorkspaceReductionResult.CommitPrepared -> Unit
                }
            },
        )
    }

/** The overlay's inputs, read from [state] through one `derivedStateOf`. */
@Composable
private fun rememberOverlayInputs(state: State<EditorRenderState>): State<LayerOverlayInputs> =
    remember(state) {
        derivedStateOf {
            val render = state.value
            LayerOverlayInputs(
                LayerPaneModel(
                    LayerPanelRowsModel(
                        layerRowsOf(render.document, render.activeFrameId),
                        render.activeLayerId,
                        render.document.totalLayerCount,
                    ),
                    render.layerNotice,
                    render.underlay,
                ),
                framePanelModel(render),
                render.appearance.controlEdge,
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
    val layers: LayerPaneModel,
    val frames: FramePanelModel,
    val edge: EditorControlEdge,
)

private data class LayerPaneModel(
    val rows: LayerPanelRowsModel,
    val notice: LayerNotice?,
    val underlay: ReferenceUnderlay?,
) {
    val active: LayerRowModel get() = requireNotNull(rows.rows.firstOrNull { it.id == rows.active })
}

private enum class LayerPane { Layers, Frames }

private class LayerOverlayPresentation(
    val pane: MutableState<LayerPane?>,
    val chipFocus: FocusRequester,
    val close: () -> Unit,
) {
    fun back() {
        if (pane.value == LayerPane.Frames) pane.value = LayerPane.Layers else close()
    }
}

private class LayerPaneActions(
    val callbacks: EditorCallbacks,
    val rows: LayerRowActions,
    val pickUnderlay: () -> Unit,
)

private class LayerPaneBounds(
    val width: Dp,
    val height: Dp,
    val edge: EditorControlEdge,
) {
    fun modifier(pane: LayerPane): Modifier {
        val maximum = if (pane == LayerPane.Frames) LayerGeometry.NOTICE_MAX_WIDTH else LayerGeometry.PANEL_WIDTH
        return Modifier
            .padding(LayerGeometry.PANEL_MARGIN)
            .width(min(maximum, (width - LayerGeometry.PANEL_MARGIN * 2).coerceAtLeast(0.dp)))
            .heightIn(max = LayerGeometry.panelMaxHeight(height, edge))
    }
}
