package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackMode
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Secondary options live in the body scroll; the transport remains reachable independently. */
@Composable
internal fun FramePlaybackOptions(
    model: FramePanelModel,
    callbacks: EditorPlaybackCallbacks,
) {
    Row {
        PlaybackModeButton(PlaybackMode.Loop, model, callbacks)
        PlaybackModeButton(PlaybackMode.Once, model, callbacks)
    }
    if (model.rows.size == 1) {
        Text(
            stringResource(R.string.playback_requires_frames),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.editorDescription(R.string.playback_requires_frames),
        )
    }
}

@Composable
private fun PlaybackModeButton(
    mode: PlaybackMode,
    model: FramePanelModel,
    callbacks: EditorPlaybackCallbacks,
) {
    val label = if (mode == PlaybackMode.Loop) R.string.playback_loop else R.string.playback_once
    TextButton(
        onClick = { callbacks.onMode(mode) },
        modifier =
            Modifier
                .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                .editorDescription(label)
                .semantics { selected = model.playback.mode == mode },
    ) { Text(stringResource(label)) }
}
