package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.lifecycle.Lifecycle

/** Synchronous Android admission, with no local playback token/index/time owner. */
internal class PlaybackUiActions(
    private val lifecycle: Lifecycle,
    private val callbacks: EditorPlaybackCallbacks,
) {
    fun start() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) callbacks.onStart() else callbacks.onStop()
    }
}
