package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun PaletteControls(
    palette: Palette,
    activePaletteIndex: PaletteIndex,
    callbacks: EditorCallbacks,
    dismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            pluralStringResource(R.plurals.palette_count, palette.entryCount, palette.entryCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            state = rememberLazyGridState(initialFirstVisibleItemIndex = activePaletteIndex.value),
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier.fillMaxSize().editorDescription(R.string.palette_colors),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(palette.entries(), key = { it.index.value }) { entry ->
                PaletteEntryControl(entry, entry.index == activePaletteIndex) {
                    callbacks.onSelectPaletteEntry(entry.index)
                    dismiss()
                }
            }
        }
    }
}

@Composable
private fun PaletteEntryControl(
    entry: PaletteEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .size(56.dp)
                .selectable(selected, role = Role.Button, onClick = onClick)
                .editorDescription(
                    R.string.palette_entry,
                    entry.index.value + 1,
                    entry.color.red.value
                        .toInt(),
                    entry.color.green.value
                        .toInt(),
                    entry.color.blue.value
                        .toInt(),
                    entry.color.alpha.value
                        .toInt(),
                    identity = "editor_palette_entry_${entry.index.value + 1}",
                ).border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).background(entry.color.toComposeColor())) {
            if (selected) {
                Text(
                    "✓",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).background(Color.Black).padding(horizontal = 2.dp),
                )
            }
        }
        Text(stringResource(R.string.entry_number, entry.index.value + 1), style = MaterialTheme.typography.labelSmall)
    }
}
