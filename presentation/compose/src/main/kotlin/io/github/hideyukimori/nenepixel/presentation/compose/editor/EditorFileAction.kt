package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** One file intent and its current presentation eligibility, without another operation owner. */
internal class EditorFileAction(
    val enabled: Boolean,
    val request: () -> Unit,
)

@Composable
internal fun rememberUnderlayPick(
    storage: EditorStorageInputs,
    playback: EditorPlaybackCallbacks,
): EditorFileAction =
    remember(storage.callbacks, storage.canStart, playback) {
        EditorFileAction(storage.canStart) {
            playback.onStop()
            storage.callbacks.pickReferenceImage()
        }
    }
