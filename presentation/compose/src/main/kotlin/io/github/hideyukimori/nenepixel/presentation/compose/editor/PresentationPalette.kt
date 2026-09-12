package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.Color
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme

internal object PresentationPalette {
    fun canvasSurround(theme: EditorTheme): Color =
        when (theme) {
            EditorTheme.Dark -> darkSurround
            EditorTheme.Light -> lightSurround
        }

    val canvasBackground: Color = Color.White
    private val darkSurround: Color = Color(0xFF292929)
    private val lightSurround: Color = Color(0xFFBDBDBD)
    val grid: Color = Color.Black.copy(alpha = GRID_ALPHA)
    val eraserPreview: Color = Color(0xFF30343B).copy(alpha = ERASER_PREVIEW_ALPHA)

    private const val GRID_ALPHA: Float = 0.16f
    private const val ERASER_PREVIEW_ALPHA: Float = 0.45f
}
