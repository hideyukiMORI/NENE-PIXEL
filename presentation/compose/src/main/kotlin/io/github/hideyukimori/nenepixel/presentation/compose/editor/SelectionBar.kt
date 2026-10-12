package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionInteraction
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Issue #211: one transient surface, with Back registered below existing panels/menus for stable priority. */
@Composable
internal fun SelectionBar(
    state: State<EditorRenderState>,
    callbacks: EditorSelectionCallbacks,
) {
    val inputs by remember(state) {
        derivedStateOf { selectionBarInputs(state.value) }
    }
    BackHandler(enabled = inputs.model != null) { callbacks.onCancel() }
    inputs.model?.let {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < NARROW_WORK_AREA
            val side = if (narrow) LayerGeometry.MARGIN else LayerGeometry.NOTICE_SIDE_MARGIN
            val bottom = if (narrow) LayerGeometry.QUICK_SELECT_CLEARANCE else LayerGeometry.MARGIN
            SelectionBarSurface(
                inputs,
                callbacks,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = side, end = side, bottom = bottom)
                    .widthIn(max = LayerGeometry.NOTICE_MAX_WIDTH),
            )
        }
    }
}

private data class SelectionBarInputs(
    val model: SelectionBarModel?,
    val notice: SelectionEditorNotice?,
)

private data class SelectionBarModel(
    val size: CanvasSize?,
    val mode: PixelTransferMode,
    val idle: Boolean,
    val canApply: Boolean,
)

private fun selectionBarInputs(state: EditorRenderState): SelectionBarInputs {
    val model =
        state.selectionSession?.let {
            SelectionBarModel(it.targetRegion?.size, it.mode, it.interaction == SelectionInteraction.Idle, it.canApply)
        }
    return SelectionBarInputs(model, state.selectionNotice)
}

@Composable
private fun SelectionBarSurface(
    inputs: SelectionBarInputs,
    callbacks: EditorSelectionCallbacks,
    modifier: Modifier,
) {
    val model = inputs.model ?: return
    val title = stringResource(R.string.selection_title)
    Surface(
        modifier.testTag("editor_selection_bar").semantics { paneTitle = title },
        shape = RoundedCornerShape(LayerGeometry.PANEL_CORNER),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(LayerGeometry.BORDER, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            Modifier.padding(LayerGeometry.PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(LayerGeometry.PANEL_SPACING),
        ) {
            SelectionStatus(model)
            SelectionModes(model, callbacks)
            Text(stringResource(R.string.selection_empty_replacement), style = MaterialTheme.typography.bodySmall)
            inputs.notice?.let { Text(stringResource(it.resource()), color = MaterialTheme.colorScheme.error) }
            SelectionActions(model, callbacks)
        }
    }
}

@Composable
private fun SelectionActions(
    model: SelectionBarModel,
    callbacks: EditorSelectionCallbacks,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LayerGeometry.GAP)) {
        TextButton(
            onClick = { callbacks.onCancel() },
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                    .editorDescription(R.string.selection_cancel),
        ) {
            Text(stringResource(R.string.selection_cancel))
        }
        Button(
            onClick = { callbacks.onApply() },
            enabled = model.canApply,
            colors = editorButtonColors(),
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                    .editorDescription(R.string.selection_apply),
        ) {
            Text(stringResource(R.string.selection_apply))
        }
    }
}

@Composable
private fun SelectionStatus(model: SelectionBarModel) {
    val size = model.size
    val status =
        if (size == null) {
            stringResource(R.string.selection_waiting)
        } else {
            stringResource(R.string.selection_size, size.width.value, size.height.value)
        }
    Text(
        status,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.testTag("editor_selection_status"),
    )
}

@Composable
private fun SelectionModes(
    model: SelectionBarModel,
    callbacks: EditorSelectionCallbacks,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LayerGeometry.GAP)) {
        listOf(PixelTransferMode.Move, PixelTransferMode.Copy).forEach { mode ->
            val label = if (mode == PixelTransferMode.Move) R.string.selection_move else R.string.selection_copy
            FilterChip(
                selected = model.mode == mode,
                onClick = { callbacks.onMode(mode) },
                enabled = model.idle,
                label = { Text(stringResource(label)) },
                modifier = Modifier.weight(1f).heightIn(min = LayerGeometry.HEADER_HEIGHT).editorDescription(label),
            )
        }
    }
}

private val NARROW_WORK_AREA = 392.dp
