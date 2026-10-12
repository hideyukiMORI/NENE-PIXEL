package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackPulseResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackToken
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackView
import kotlinx.coroutines.delay

/** One Frames-owned driver: stable current-session lifecycle stop, token-checked asynchronous disposal. */
@Composable
internal fun framePlaybackAdapter(
    view: PlaybackView,
    callbacks: EditorPlaybackCallbacks,
): PlaybackUiActions {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, callbacks) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) callbacks.onStop()
            }
        lifecycle.addObserver(observer)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) callbacks.onStop()
        onDispose { lifecycle.removeObserver(observer) }
    }
    val token = view.token
    DisposableEffect(token, callbacks, lifecycle) {
        onDispose { token?.let(callbacks::onStopIfCurrent) }
    }
    LaunchedEffect(token, callbacks, lifecycle) {
        token?.let { drivePlayback(lifecycle, callbacks, it) }
    }
    return remember(lifecycle, callbacks) { PlaybackUiActions(lifecycle, callbacks) }
}

private suspend fun drivePlayback(
    lifecycle: Lifecycle,
    callbacks: EditorPlaybackCallbacks,
    token: PlaybackToken,
) {
    while (admitPlaybackSample(lifecycle, callbacks, token)) {
        val nanos = withFrameNanos { it }
        if (!admitPlaybackSample(lifecycle, callbacks, token)) return
        when (val result = callbacks.onSample(token, nanos)) {
            is PlaybackPulseResult.Continue -> delay(playbackDelayMillis(result.remainingNanos))
            is PlaybackPulseResult.Stopped, PlaybackPulseResult.Stale -> return
        }
    }
}

/** Rechecked after delay and frame suspension; a late callback never advances a background token. */
private fun admitPlaybackSample(
    lifecycle: Lifecycle,
    callbacks: EditorPlaybackCallbacks,
    token: PlaybackToken,
): Boolean {
    val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    if (!started) callbacks.onStopIfCurrent(token)
    return started
}

internal fun playbackDelayMillis(remainingNanos: Long): Long =
    remainingNanos / NANOS_PER_MILLISECOND + if (remainingNanos % NANOS_PER_MILLISECOND == 0L) 0L else 1L

private const val NANOS_PER_MILLISECOND = 1_000_000L
