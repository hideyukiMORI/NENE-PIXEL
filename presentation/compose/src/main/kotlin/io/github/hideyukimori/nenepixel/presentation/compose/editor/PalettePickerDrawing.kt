package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Drawing of picker controls only, using the same checkerboard as the artwork. */
internal class PalettePickerDrawing {
    private val backdrop = TransparencyBackdrop()
    private val area = RectF()

    fun plane(
        scope: DrawScope,
        hsv: PaletteHsvColor,
    ) {
        with(scope) {
            val pure = PaletteHsvColor(hsv.hue, 1f, 1f).toColor(PaletteHsvColor.CHANNEL_MAX).toComposeColor()
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            marker(Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height))
        }
    }

    fun bar(
        scope: DrawScope,
        bar: PalettePickerBar,
    ) {
        with(scope) {
            val height = 20.dp.toPx()
            val top = (size.height - height) / 2f
            area.set(0f, top, size.width, top + height)
            if (bar.axis == PalettePickerAxis.Alpha) drawTransparencyBackdrop(backdrop, area)
            drawRect(Brush.horizontalGradient(bar.colors), Offset(0f, top), Size(size.width, height))
            marker(Offset(bar.fraction * size.width, size.height / 2f))
        }
    }

    private fun DrawScope.marker(position: Offset) {
        val radius = 7.dp.toPx()
        val center =
            Offset(
                position.x.coerceIn(radius, size.width - radius),
                position.y.coerceIn(
                    radius,
                    size.height - radius,
                ),
            )
        drawCircle(Color.Black, radius + 1.dp.toPx(), center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
    }
}

internal enum class PalettePickerAxis { Hue, Alpha }

internal data class PalettePickerBar(
    val axis: PalettePickerAxis,
    val fraction: Float,
    val colors: List<Color>,
)
