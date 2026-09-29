package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val actualSizeWindowFrame: Color = Color(0xFF5E2750)

    /** Grip and scale-chip fill: a fixed light tint that stays legible on the frame in both themes. */
    val actualSizeWindowLabel: Color = Color(0xFFF2E9EF)

    /** Transparency backdrop light cell (ADR 0026). Fixed in both themes; the top-left cell. */
    val transparencyLight: Color = Color(0xFFFFFFFF)

    /** Transparency backdrop dark cell (ADR 0026). Fixed in both themes. */
    val transparencyDark: Color = Color(0xFFD9D9D9)

    /** Transparency backdrop cell edge, fixed to the screen: zoom never changes it (ADR 0026). */
    val transparencyCell: Dp = 8.dp

    private const val GRID_ALPHA: Float = 0.16f
}
