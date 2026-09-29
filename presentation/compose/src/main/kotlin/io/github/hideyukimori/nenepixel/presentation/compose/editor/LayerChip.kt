package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The closed layer panel (#144 UI spec "チップ"): the layers mark, the active layer's name, and a closed-eye
 * mark while that layer is hidden. It looks 40dp tall but takes touches over at least 48dp.
 */
@Composable
internal fun LayerChip(
    active: LayerRowModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val label = layerLabel(active)
    val hidden = active.visibility == LayerVisibility.Hidden
    val state = stringResource(if (hidden) R.string.layer_chip_state_hidden else R.string.layer_chip_state, label)
    val shape = RoundedCornerShape(LayerGeometry.CHIP_CORNER)
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .widthIn(LayerGeometry.CHIP_MIN_WIDTH, LayerGeometry.CHIP_MAX_WIDTH)
            .heightIn(min = LayerGeometry.CHIP_MIN_HEIGHT)
            .clip(shape)
            .background(scheme.surface, shape)
            .border(LayerGeometry.BORDER, scheme.primary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .editorDescription(R.string.layer_chip)
            .semantics { stateDescription = state }
            .padding(horizontal = LayerGeometry.CHIP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(LayerGeometry.GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChipSymbol(EditorIcon.Layers)
        Text(
            label,
            color = scheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (hidden) ChipSymbol(EditorIcon.Hidden)
    }
}

@Composable
private fun ChipSymbol(icon: EditorIcon) {
    Icon(
        painter = painterResource(icon.drawable),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(LayerGeometry.SYMBOL_SIZE),
    )
}
