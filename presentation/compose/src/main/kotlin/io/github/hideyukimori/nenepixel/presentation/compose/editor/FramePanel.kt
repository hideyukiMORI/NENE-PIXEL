package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameLimits
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** One secondary pane; only rows scroll while blank Add and global capacity remain reachable. */
@Composable
internal fun FramePanel(
    model: FramePanelModel,
    actions: FramePanelActions,
    modifier: Modifier,
) {
    val playbackUi = framePlaybackAdapter(model.playback, actions.playback)
    var durationTarget by remember { mutableStateOf<FrameId?>(null) }
    val title = stringResource(R.string.frames)
    Surface(
        modifier.testTag("editor_frame_panel").semantics { paneTitle = title },
        shape = RoundedCornerShape(LayerGeometry.PANEL_CORNER),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(LayerGeometry.BORDER, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            Modifier.framePanelPadding(),
            verticalArrangement = Arrangement.spacedBy(LayerGeometry.PANEL_SPACING),
        ) {
            FramePanelHeading(model, actions)
            FramePanelRows(model, actions) {
                actions.playback.onStop()
                durationTarget = it
            }
            model.notice?.let { Text(stringResource(it.resource()), color = MaterialTheme.colorScheme.error) }
            FrameCapacityFooter(model, actions, playbackUi)
        }
    }
    val target = model.rows.firstOrNull { it.id == durationTarget }
    LaunchedEffect(target == null) { if (target == null) durationTarget = null }
    target?.let { FrameDurationDialog(it, model.rows.indexOf(it) + 1, actions.callbacks) { durationTarget = null } }
}

@Composable
private fun FramePanelHeading(
    model: FramePanelModel,
    actions: FramePanelActions,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = LayerGeometry.HEADER_HEIGHT)) {
        Text(
            stringResource(R.string.frame_count, model.rows.size, FrameLimits.MAX_FRAMES),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        IconButton(onClick = actions.back, modifier = Modifier.editorDescription(R.string.frame_panel_back)) {
            EditorSymbol(EditorIcon.Close)
        }
    }
}

@Composable
private fun FrameCapacityFooter(
    model: FramePanelModel,
    actions: FramePanelActions,
    ui: PlaybackUiActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LayerGeometry.PANEL_SPACING)) {
        FrameLayerCapacity(model.capacity)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LayerGeometry.PANEL_SPACING)) {
            FramePlaybackTransport(model, actions.playback, ui, Modifier.weight(1f))
            Button(
                onClick = { actions.callbacks.onAdd(model.active) },
                enabled = model.capacity.canAdd,
                colors = editorButtonColors(),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                        .editorDescription(R.string.frame_add),
            ) { Text(stringResource(R.string.frame_add_compact)) }
        }
    }
}

/** Keep one scrollable 48dp body row at short height, with the transport/capacity still fixed. */
private fun Modifier.framePanelPadding(): Modifier =
    padding(
        start = LayerGeometry.PANEL_PADDING,
        end = LayerGeometry.PANEL_PADDING,
        top = LayerGeometry.PANEL_TOP_PADDING,
        bottom = LayerGeometry.PANEL_TOP_PADDING,
    )

@Composable
private fun FrameLayerCapacity(capacity: FrameCapacity) {
    val full = capacity.remainingLayers == 0
    val label = if (full) R.string.frame_layer_capacity_full else R.string.document_layer_capacity
    val identity = if (full) "editor_document_layer_limit" else "editor_document_layer_capacity"
    Text(
        stringResource(label, capacity.layerCount, FrameLimits.MAX_TOTAL_LAYER_RECORDS, capacity.remainingLayers),
        style = MaterialTheme.typography.bodySmall,
        modifier =
            Modifier.editorDescription(
                label,
                capacity.layerCount,
                FrameLimits.MAX_TOTAL_LAYER_RECORDS,
                capacity.remainingLayers,
                identity = identity,
            ),
    )
}
