package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Secondary sections reveal their controls without occupying primary picker space. */
@Composable
internal fun PaletteEditorDisclosure(
    label: Int,
    expanded: Boolean,
    toggle: () -> Unit,
) {
    val state = stringResource(if (expanded) R.string.palette_editor_expanded else R.string.palette_editor_collapsed)
    TextButton(
        onClick = toggle,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .editorDescription(label)
                .semantics { stateDescription = state },
    ) {
        Text(stringResource(label))
        Text(if (expanded) " ▴" else " ▾")
    }
}
