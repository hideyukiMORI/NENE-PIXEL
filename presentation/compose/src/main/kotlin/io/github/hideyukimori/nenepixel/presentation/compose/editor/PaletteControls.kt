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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

@Composable
internal fun PaletteControls(
    palette: Palette,
    activePaletteIndex: PaletteIndex,
    callbacks: EditorCallbacks,
    dismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${palette.entryCount} colors · Select a drawing color",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            state = rememberLazyGridState(initialFirstVisibleItemIndex = activePaletteIndex.value),
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Palette colors" },
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
                .semantics { contentDescription = entry.description() }
                .border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
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
        Text("${entry.index.value + 1}", style = MaterialTheme.typography.labelSmall)
    }
}

private fun PaletteEntry.description(): String =
    "Palette color ${index.value + 1}, " +
        "RGBA ${color.red.value}, ${color.green.value}, ${color.blue.value}, ${color.alpha.value}"
