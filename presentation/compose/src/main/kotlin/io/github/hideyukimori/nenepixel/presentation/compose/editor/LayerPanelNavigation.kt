package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** These callbacks change only the secondary pane, never editor focus. */
internal class LayerPanelNavigation(
    val close: () -> Unit,
    val frames: () -> Unit,
)
