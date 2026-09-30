package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The open layer panel (#144 UI spec "パネル"): a heading row with the layer count and the close button, then
 * [content], which holds the row list and the add row.
 *
 * The non-clickable `Surface` takes every pointer over the panel, so none reaches the canvas beneath it.
 */
@Composable
internal fun LayerPanel(
    count: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.layers)
    Surface(
        modifier = modifier.testTag(PANEL_TAG).semantics { paneTitle = title },
        shape = RoundedCornerShape(LayerGeometry.PANEL_CORNER),
        color = scheme.surface,
        border = BorderStroke(LayerGeometry.BORDER, scheme.primary),
    ) {
        Column(
            Modifier.padding(
                start = LayerGeometry.PANEL_PADDING,
                top = LayerGeometry.PANEL_TOP_PADDING,
                end = LayerGeometry.PANEL_PADDING,
                bottom = LayerGeometry.PANEL_PADDING,
            ),
            verticalArrangement = Arrangement.spacedBy(LayerGeometry.PANEL_SPACING),
        ) {
            LayerPanelHeading(title, count, onClose)
            content()
        }
    }
}

@Composable
private fun LayerPanelHeading(
    title: String,
    count: Int,
    onClose: () -> Unit,
) {
    val countDescription = stringResource(R.string.layer_count_description, count, LayerLimits.MAX_LAYERS)
    Row(
        Modifier.fillMaxWidth().heightIn(min = LayerGeometry.HEADER_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(LayerGeometry.GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.layer_count, count, LayerLimits.MAX_LAYERS),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics { contentDescription = countDescription },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.editorDescription(R.string.layer_panel_close)) {
            EditorSymbol(EditorIcon.Close)
        }
    }
}

private const val PANEL_TAG: String = "editor_layer_panel"
