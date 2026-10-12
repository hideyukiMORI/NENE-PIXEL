package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameLimits
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Logical layer records across every frame, including shared immutable snapshots. */
@Composable
internal fun DocumentLayerCapacity(
    count: Int,
    limitIdentity: String = "editor_document_layer_limit",
) {
    Text(
        stringResource(
            R.string.document_layer_capacity,
            count,
            FrameLimits.MAX_TOTAL_LAYER_RECORDS,
            FrameLimits.MAX_TOTAL_LAYER_RECORDS - count,
        ),
        style = MaterialTheme.typography.bodySmall,
        modifier =
            Modifier.editorDescription(
                R.string.document_layer_capacity,
                count,
                FrameLimits.MAX_TOTAL_LAYER_RECORDS,
                FrameLimits.MAX_TOTAL_LAYER_RECORDS - count,
            ),
    )
    if (count >= FrameLimits.MAX_TOTAL_LAYER_RECORDS) {
        Text(
            stringResource(R.string.document_layer_limit),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.editorDescription(R.string.document_layer_limit, identity = limitIdentity),
        )
    }
}
