package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession

/** Shared palette editing and frame preview inputs keep the controls' boundary cohesive. */
internal class PaletteEditorView(
    val session: PaletteEditSession,
    val notice: PaletteEditorNotice?,
    val frames: PaletteFrameChoice,
)
