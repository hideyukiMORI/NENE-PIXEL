package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/**
 * The quick-select control and its fan over the work area (ADR 0029 "Floating control").
 *
 * It reads the quick selection, the active slot, the palette definition and the control edge through one
 * `derivedStateOf`, so a stroke in progress never recomposes it. Drag versus tap mode is local input state; the
 * menu and its highlight always come from the render state.
 */
@Composable
internal fun QuickSelectOverlay(
    state: State<EditorRenderState>,
    callbacks: EditorCallbacks,
) {
    val inputs by remember(state) {
        derivedStateOf {
            val render = state.value
            QuickSelectInputs(
                render.quickSelection,
                render.activePaletteIndex,
                render.definition,
                render.appearance.controlEdge,
            )
        }
    }
    var tapMode by remember { mutableStateOf(false) }
    val menu = inputs.selection.menu
    LaunchedEffect(menu == null) { if (menu == null) tapMode = false }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val area = IntSize(constraints.maxWidth, constraints.maxHeight)
        val geometry = remember(area, inputs.edge, density) { QuickSelectGeometry.create(area, inputs.edge, density) }
        val placement = menu?.let { open -> remember(open.items, geometry) { geometry.placement(open.items) } }
        val armed = inputs.selection.eyedropper == EyedropperState.Armed
        val session = rememberUpdatedState(QuickSelectGestureSession(armed, placement, tapMode))
        if (placement != null) {
            QuickSelectFan(placement, menu.highlighted, inputs.definition.palette)
        }
        QuickSelectControl(
            inputs.controlDisplay(armed),
            Modifier
                .align(inputs.corner)
                .padding(QuickSelectGeometry.MARGIN)
                .quickSelectGesture(callbacks.quickSelect, session) { tapMode = it },
        )
    }
}

private data class QuickSelectInputs(
    val selection: QuickSelection,
    val active: PaletteIndex,
    val definition: PaletteDefinition,
    val edge: EditorControlEdge,
) {
    val corner: Alignment
        get() =
            when (edge) {
                EditorControlEdge.Left -> AbsoluteAlignment.BottomLeft
                EditorControlEdge.Right -> AbsoluteAlignment.BottomRight
            }

    fun controlDisplay(armed: Boolean): QuickSelectControlDisplay =
        QuickSelectControlDisplay(
            definition.palette.quickSelectColor(QuickSelectSemantics.shownSlot(selection, active)),
            armed,
            QuickSelectSemantics.controlState(selection.eyedropper, active),
        )
}
