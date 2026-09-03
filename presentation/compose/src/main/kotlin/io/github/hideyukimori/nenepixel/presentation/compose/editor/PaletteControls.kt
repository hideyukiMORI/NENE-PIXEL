package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry

@Composable
internal fun PaletteControls(
    renderState: EditorRenderState,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PALETTE_LABEL_SPACING),
    ) {
        Text(text = "Palette")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PALETTE_ENTRY_SPACING, Alignment.CenterHorizontally),
        ) {
            items(renderState.palette.entries(), key = { entry -> entry.index.value }) { entry ->
                PaletteEntryControl(
                    entry = entry,
                    selected = entry.index == renderState.activePaletteIndex,
                    onClick = { onRenderStateChanged(callbacks.onSelectPaletteEntry(entry.index)) },
                )
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
    val borderWidth = if (selected) SELECTED_BORDER_WIDTH else UNSELECTED_BORDER_WIDTH
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val description =
        "Palette color ${entry.index.value + 1}, " +
            "RGBA ${entry.color.red.value}, ${entry.color.green.value}, " +
            "${entry.color.blue.value}, ${entry.color.alpha.value}"
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .size(PALETTE_ENTRY_SIZE)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }.border(borderWidth, borderColor, PALETTE_ENTRY_SHAPE)
                .padding(PALETTE_ENTRY_INSET)
                .background(entry.color.toComposeColor(), PALETTE_ENTRY_SHAPE)
                .clickable(onClick = onClick),
    )
}

private val PALETTE_ENTRY_SHAPE = RoundedCornerShape(4.dp)
private val PALETTE_ENTRY_SIZE = 40.dp
private val PALETTE_ENTRY_SPACING = 4.dp
private val PALETTE_LABEL_SPACING = 4.dp
private val PALETTE_ENTRY_INSET = 2.dp
private val SELECTED_BORDER_WIDTH = 3.dp
private val UNSELECTED_BORDER_WIDTH = 1.dp
