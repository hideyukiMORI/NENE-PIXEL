package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** A chronological row identified by FrameId; pixel storage is never read here. */
@Composable
internal fun FrameRow(
    entry: FrameRowEntry,
    actions: FramePanelActions,
    duration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = stringResource(R.string.frame_current)
    Row(Modifier.fillMaxWidth().heightIn(min = LayerGeometry.HEADER_HEIGHT)) {
        Text(
            stringResource(R.string.frame_row, entry.position + 1, entry.row.duration.value),
            color = if (entry.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier =
                modifier
                    .weight(1f)
                    .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                    .selectable(entry.current, role = Role.Button) { actions.select(entry.row.id) }
                    .editorDescription(
                        R.string.frame_row,
                        entry.position + 1,
                        entry.row.duration.value,
                        identity = "editor_frame_row_${entry.row.id.value}",
                    ).semantics { if (entry.current) stateDescription = current }
                    .padding(LayerGeometry.PANEL_PADDING),
        )
        FrameRowMenu(entry, actions.callbacks, duration)
    }
}
