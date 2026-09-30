package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * Asks before a close request discards a changed palette draft (#165). Discard takes the existing Cancel route; Keep
 * editing, a tap outside and Back only close the confirmation. The confirmation closes with the draft session.
 */
@Composable
internal fun PaletteEditorDiscardConfirmation(
    state: State<EditorRenderState>,
    confirming: Boolean,
    callbacks: EditorPaletteCallbacks,
    close: () -> Unit,
) {
    val editing by remember(state) { derivedStateOf { state.value.paletteEditSession != null } }
    LaunchedEffect(editing) {
        if (!editing) close()
    }
    if (confirming && editing) {
        DiscardDialog(
            discard = {
                callbacks.onCancel()
                close()
            },
            keep = close,
        )
    }
}

@Composable
private fun DiscardDialog(
    discard: () -> Unit,
    keep: () -> Unit,
) {
    AlertDialog(
        modifier =
            Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("editor_palette_editor_discard_dialog"),
        onDismissRequest = keep,
        title = { Text(stringResource(R.string.palette_editor_discard_title)) },
        text = { Text(stringResource(R.string.palette_editor_discard_text)) },
        confirmButton = {
            Button(
                colors = editorButtonColors(),
                modifier =
                    Modifier.editorDescription(
                        R.string.discard,
                        identity = "editor_palette_editor_discard_confirm",
                    ),
                onClick = discard,
            ) {
                Text(stringResource(R.string.discard))
            }
        },
        dismissButton = {
            TextButton(
                onClick = keep,
                modifier =
                    Modifier.editorDescription(
                        R.string.palette_editor_keep_editing,
                        identity = "editor_palette_editor_discard_keep",
                    ),
            ) {
                Text(stringResource(R.string.palette_editor_keep_editing))
            }
        },
    )
}
