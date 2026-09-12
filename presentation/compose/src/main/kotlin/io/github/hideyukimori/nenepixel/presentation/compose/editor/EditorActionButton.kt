package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** One resource supplies visible text, accessible action meaning and stable operation identity. */
@Composable
internal fun EditorActionButton(
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        colors = editorButtonColors(),
        modifier = Modifier.editorDescription(label),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(stringResource(label))
    }
}
