package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The panel's add row (#144 UI spec "パネル" 3): the full-width "Add layer" button, which never scrolls and so stays
 * at the panel's bottom edge. At [LayerLimits.MAX_LAYERS] layers the button is disabled and the limit is stated
 * beneath it. It reads only [layerCount], which a stroke never changes.
 */
@Composable
internal fun LayerPanelAddRow(
    layerCount: Int,
    onAdd: () -> Unit,
) {
    val full = layerCount >= LayerLimits.MAX_LAYERS
    Column(verticalArrangement = Arrangement.spacedBy(LayerGeometry.PANEL_SPACING)) {
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = ADD_HEIGHT).editorDescription(R.string.layer_add),
            enabled = !full,
            colors = editorButtonColors(),
        ) {
            EditorSymbol(EditorIcon.Add)
            Spacer(Modifier.width(LayerGeometry.GAP))
            Text(stringResource(R.string.layer_add))
        }
        if (full) {
            Text(
                pluralStringResource(R.plurals.layer_limit, LayerLimits.MAX_LAYERS, LayerLimits.MAX_LAYERS),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(LIMIT_TAG),
            )
        }
    }
}

private val ADD_HEIGHT: Dp = 48.dp
private const val LIMIT_TAG: String = "editor_layer_limit"
