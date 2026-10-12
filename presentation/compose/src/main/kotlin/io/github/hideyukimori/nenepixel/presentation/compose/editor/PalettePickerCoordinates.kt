package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

/** Local undefined HSV coordinates only; canonical RGBA is always supplied by the palette session. */
internal class PalettePickerCoordinates(
    color: PixelColor,
) {
    var hsv: PaletteHsvColor by mutableStateOf(PaletteHsvColor.fromColor(color))
        private set

    fun synchronize(
        color: PixelColor,
        editing: Boolean = false,
    ) {
        hsv = current(color, editing)
    }

    /** Active pointer coordinates keep hue/saturation stable across lagged, quantized preview publications. */
    fun current(
        color: PixelColor,
        editing: Boolean = false,
    ): PaletteHsvColor =
        if (editing || hsv.toColor(color.alpha.value.toInt()) == color) hsv else PaletteHsvColor.fromColor(color, hsv)

    fun colorAt(
        next: PaletteHsvColor,
        canonical: PixelColor,
    ): PixelColor {
        hsv = next
        return next.toColor(canonical.alpha.value.toInt())
    }
}
