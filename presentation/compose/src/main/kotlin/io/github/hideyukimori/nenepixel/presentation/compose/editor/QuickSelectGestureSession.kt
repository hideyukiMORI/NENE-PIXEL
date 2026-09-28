package io.github.hideyukimori.nenepixel.presentation.compose.editor

/**
 * What one press on the quick-select control needs to know when it starts and while it moves (ADR 0029). The
 * gesture reads it through a [State] so a recomposition never restarts a press in progress.
 */
internal data class QuickSelectGestureSession(
    val armed: Boolean,
    val placement: QuickSelectFanPlacement?,
    val tapMode: Boolean,
)
