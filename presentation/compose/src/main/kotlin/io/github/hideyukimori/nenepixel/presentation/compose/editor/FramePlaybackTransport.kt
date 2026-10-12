package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Always in the fixed footer; the editing row stays selected while this label speaks the displayed frame. */
@Composable
internal fun FramePlaybackTransport(
    model: FramePanelModel,
    callbacks: EditorPlaybackCallbacks,
    ui: PlaybackUiActions,
    modifier: Modifier,
) {
    val playing = model.playback.token != null
    val ordinal = model.rows.indexOfFirst { it.id == model.frames.displayedFrameId } + 1
    val label = if (playing) R.string.playback_stop_frame else R.string.playback_play
    val identity = if (playing) "editor_playback_stop" else "editor_playback_play"
    val description = if (playing) R.string.playback_stop_description else label
    Button(
        onClick = { if (playing) callbacks.onStop() else ui.start() },
        enabled = playing || model.rows.size > 1,
        colors = editorButtonColors(),
        modifier =
            modifier
                .heightIn(min = LayerGeometry.HEADER_HEIGHT)
                .editorDescription(description, ordinal, model.rows.size, identity = identity),
    ) { Text(stringResource(label, ordinal, model.rows.size)) }
}
