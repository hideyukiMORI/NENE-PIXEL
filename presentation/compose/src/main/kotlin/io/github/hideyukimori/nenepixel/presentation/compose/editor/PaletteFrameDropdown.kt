package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Selecting closes this popup only; the palette Dialog and its draft timeline stay open. */
@Composable
internal fun PaletteFrameDropdown(
    choice: PaletteFrameChoice,
    enabled: Boolean,
) {
    val rows = choice.model.rows
    if (rows.size <= 1) return
    var expanded by remember { mutableStateOf(false) }
    val ordinal = rows.indexOfFirst { it.id == choice.model.active } + 1
    Column {
        Box {
            TextButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                        .editorDescription(R.string.frame_preview, ordinal, rows.size),
            ) {
                Text(stringResource(R.string.frame_preview, ordinal, rows.size))
            }
            PaletteFramePopup(choice, expanded) { expanded = false }
        }

        choice.model.notice?.let { Text(stringResource(it.resource()), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PaletteFramePopup(
    choice: PaletteFrameChoice,
    expanded: Boolean,
    dismiss: () -> Unit,
) {
    DropdownMenu(expanded, onDismissRequest = dismiss) {
        choice.model.rows.forEachIndexed { index, row ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.frame_label, index + 1)) },
                onClick = {
                    dismiss()
                    choice.callbacks.onSelect(row.id)
                },
                modifier =
                    Modifier.editorDescription(
                        R.string.frame_label,
                        index + 1,
                        identity = "editor_palette_frame_${row.id.value}",
                    ),
            )
        }
    }
}
