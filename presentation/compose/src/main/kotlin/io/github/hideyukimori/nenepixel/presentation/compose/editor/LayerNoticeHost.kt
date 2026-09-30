package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/**
 * Shows the render state's layer [notice] as one material3 snackbar (#144 U6). A notice with an action stays
 * `Long`, one without stays `Short`. Whatever ends the snackbar (timeout, the action, or leaving composition), the
 * host reports the notice settled, so reopening the panel or rotating the screen does not show it again. When the
 * notice is dropped (the document moved on), the running snackbar is dismissed.
 */
@Composable
internal fun LayerNoticeHost(
    notice: LayerNotice?,
    callbacks: EditorCallbacks,
    modifier: Modifier = Modifier,
) {
    val host = remember { SnackbarHostState() }
    val message = notice?.let { stringResource(it.kind.message) }
    val action = notice?.kind?.action?.let { stringResource(it) }
    LaunchedEffect(notice) {
        if (notice != null && message != null) {
            host.show(notice, message, action, callbacks)
        } else {
            host.currentSnackbarData?.dismiss()
        }
    }
    SnackbarHost(host, modifier.testTag(LAYER_NOTICE_TAG)) { data -> Snackbar(data) }
}

private suspend fun SnackbarHostState.show(
    notice: LayerNotice,
    message: String,
    action: String?,
    callbacks: EditorCallbacks,
) {
    try {
        val duration = if (action == null) SnackbarDuration.Short else SnackbarDuration.Long
        if (showSnackbar(message, action, duration = duration) == SnackbarResult.ActionPerformed) {
            callbacks.perform(notice)
        }
    } finally {
        callbacks.layers.onNoticeSettled(notice.serial)
    }
}

/** The notice's action: undo through the dock's route, or show the hidden target layer. */
private fun EditorCallbacks.perform(notice: LayerNotice) {
    when (notice.kind) {
        LayerNotice.Kind.Deleted -> onUndo()
        LayerNotice.Kind.HiddenTarget -> notice.target?.let { layers.onSetVisibility(it, LayerVisibility.Visible) }
        LayerNotice.Kind.Busy, LayerNotice.Kind.Failed -> Unit
    }
}

private const val LAYER_NOTICE_TAG: String = "editor_layer_notice"
