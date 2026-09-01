package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.Color

internal object PresentationPalette {
    val editorBackground: Color = Color(0xFFF1F1F4)
    val canvasBackground: Color = Color.White
    val grid: Color = Color.Black.copy(alpha = GRID_ALPHA)

    private const val GRID_ALPHA: Float = 0.16f
}
