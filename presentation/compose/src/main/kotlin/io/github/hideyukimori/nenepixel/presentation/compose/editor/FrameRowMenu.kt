package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Commands target this stable row, including inactive rows. */
@Composable
internal fun FrameRowMenu(
    entry: FrameRowEntry,
    callbacks: EditorFrameCallbacks,
    duration: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val id = entry.row.id
    Box {
        IconButton(
            onClick = { open = true },
            modifier =
                Modifier.editorDescription(
                    R.string.frame_more,
                    entry.position + 1,
                    identity = "editor_frame_more_${id.value}",
                ),
        ) { EditorSymbol(EditorIcon.More) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            val choose = { action: () -> Unit ->
                open = false
                action()
            }
            FrameMenuItems(entry, callbacks, duration, choose)
        }
    }
}

@Composable
private fun FrameMenuItems(
    entry: FrameRowEntry,
    callbacks: EditorFrameCallbacks,
    duration: () -> Unit,
    choose: (() -> Unit) -> Unit,
) {
    val id = entry.row.id
    FrameMenuItem(R.string.frame_duplicate, entry.canDuplicate) { choose { callbacks.onDuplicate(id) } }
    FrameMenuItem(R.string.frame_earlier, entry.canMoveEarlier) {
        choose { callbacks.onMove(id, entry.position - 1) }
    }
    FrameMenuItem(R.string.frame_later, entry.canMoveLater) {
        choose { callbacks.onMove(id, entry.position + 1) }
    }
    FrameMenuItem(R.string.frame_duration, true) { choose(duration) }
    FrameMenuItem(R.string.frame_delete, entry.canDelete) { choose { callbacks.onDelete(id) } }
}

@Composable
private fun FrameMenuItem(
    label: Int,
    enabled: Boolean,
    action: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = action,
        enabled = enabled,
        modifier = Modifier.editorDescription(label),
    )
}
