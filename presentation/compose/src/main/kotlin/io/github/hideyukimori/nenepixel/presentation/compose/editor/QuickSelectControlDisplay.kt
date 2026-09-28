package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.Color

/** What the control shows: the swatch colour, whether the eyedropper is armed and the spoken state. */
internal data class QuickSelectControlDisplay(
    val swatch: Color,
    val armed: Boolean,
    val state: QuickSelectLabel,
)
