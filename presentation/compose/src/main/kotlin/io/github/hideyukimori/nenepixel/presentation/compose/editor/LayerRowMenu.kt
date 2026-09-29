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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * A row's "more" button and its menu (#144 UI spec "その他"): rename, move up, move down and delete.
 *
 * Open or closed is local Compose state inside the row's `key`, so the menu goes away with its layer's row. The
 * material3 menu composes its items only while open. Choosing an item closes the menu first. Move up and move down
 * send the neighbouring position counted from the bottom; delete asks for no confirmation, and its outcome is left
 * to the runtime adapter (#144 U5 ruling).
 */
@Composable
internal fun LayerRowMenu(
    entry: LayerRowEntry,
    actions: LayerRowActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val id = entry.row.id
    val callbacks = actions.callbacks
    val label = layerLabel(entry.row)
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.editorDescription(R.string.layer_more, label, identity = MORE_TAG + id.value),
        ) {
            EditorSymbol(EditorIcon.More)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val choose = { action: () -> Unit ->
                expanded = false
                action()
            }
            LayerMenuItem(R.string.layer_rename, RENAME_TAG, enabled = true) { choose { actions.onRename(id) } }
            LayerMenuItem(R.string.layer_move_up, MOVE_UP_TAG, enabled = !entry.frontMost) {
                choose { callbacks.onMove(id, entry.moveUpPosition) }
            }
            LayerMenuItem(R.string.layer_move_down, MOVE_DOWN_TAG, enabled = !entry.backMost) {
                choose { callbacks.onMove(id, entry.moveDownPosition) }
            }
            LayerMenuItem(R.string.layer_delete, DELETE_TAG, enabled = !entry.only) {
                choose { callbacks.onDelete(id) }
            }
        }
    }
}

@Composable
private fun LayerMenuItem(
    label: Int,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        enabled = enabled,
    )
}

private const val MORE_TAG: String = "editor_layer_more_"
private const val RENAME_TAG: String = "editor_layer_rename"
private const val MOVE_UP_TAG: String = "editor_layer_move_up"
private const val MOVE_DOWN_TAG: String = "editor_layer_move_down"
private const val DELETE_TAG: String = "editor_layer_delete"
