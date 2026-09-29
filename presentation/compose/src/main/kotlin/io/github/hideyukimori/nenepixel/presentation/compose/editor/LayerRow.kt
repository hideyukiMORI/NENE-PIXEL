package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * One layer-panel row (#144 UI spec "行"): the visibility toggle, then the selectable name area with the pencil mark
 * on the active layer, then [trailing] (the "more" button's 48dp place; empty until U5 fills it).
 *
 * The toggle, the name area and [trailing] sit side by side and never overlap, so the focus order inside a row is
 * toggle → row → more. [modifier] lands on the selectable name area, so a focus requester there focuses the row.
 * The row reads only [entry]; [callbacks] is the controller's one stable instance.
 */
@Composable
internal fun LayerRow(
    entry: LayerRowEntry,
    callbacks: EditorLayerCallbacks,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { Spacer(Modifier.size(TRAILING_WIDTH)) },
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ROW_CORNER)
    val background = if (entry.current) scheme.primaryContainer else Color.Transparent
    val content = if (entry.current) scheme.onPrimaryContainer else scheme.onSurface
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .clip(shape)
                .background(background, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisibilityToggle(entry, callbacks)
            LayerRowName(entry, callbacks, modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
private fun VisibilityToggle(
    entry: LayerRowEntry,
    callbacks: EditorLayerCallbacks,
) {
    val row = entry.row
    val identity = VISIBILITY_TAG_PREFIX + row.id.value
    IconButton(
        onClick = { callbacks.onSetVisibility(row.id, entry.toggleTarget) },
        modifier = Modifier.editorDescription(entry.toggleDescription, layerLabel(row), identity = identity),
    ) {
        EditorSymbol(entry.visibilityIcon)
    }
}

@Composable
private fun LayerRowName(
    entry: LayerRowEntry,
    callbacks: EditorLayerCallbacks,
    modifier: Modifier,
) {
    val row = entry.row
    val label = layerLabel(row)
    val state = entry.stateDescription?.let { resource -> stringResource(resource) }
    val nameColor =
        if (entry.hidden && !entry.current) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current
    Row(
        modifier
            .heightIn(min = ROW_MIN_HEIGHT)
            .selectable(entry.current, role = Role.Button) { if (!entry.current) callbacks.onSelect(row.id) }
            .editorDescription(R.string.layer_row, label, identity = ROW_TAG_PREFIX + row.id.value)
            .semantics { state?.let { stateDescription = it } }
            .padding(start = NAME_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = nameColor,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.current) {
            Icon(
                painter = painterResource(EditorIcon.Pencil.drawable),
                contentDescription = null,
                modifier = Modifier.size(LayerGeometry.SYMBOL_SIZE),
            )
        }
    }
}

private val ROW_MIN_HEIGHT: Dp = 56.dp
private val ROW_CORNER: Dp = 6.dp
private val NAME_GAP: Dp = 4.dp
private val TRAILING_WIDTH: Dp = 48.dp
private const val ROW_TAG_PREFIX: String = "editor_layer_row_"
private const val VISIBILITY_TAG_PREFIX: String = "editor_layer_visibility_"
