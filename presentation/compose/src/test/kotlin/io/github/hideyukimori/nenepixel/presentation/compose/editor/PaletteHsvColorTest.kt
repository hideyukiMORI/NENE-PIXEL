package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Issue #168: canonical RGBA8 synchronization and undefined picker coordinates. */
internal class PaletteHsvColorTest {
    @Test
    fun `primary and secondary hues have known coordinates`() {
        val colors = listOf(0xFF0000FF, 0xFFFF00FF, 0x00FF00FF, 0x00FFFFFF, 0x0000FFFF, 0xFF00FFFF)
        colors.forEachIndexed { index, packed ->
            val hsv = PaletteHsvColor.fromColor(PixelColor.fromPackedRgba8888(packed.toInt()))
            assertEquals(index * 60f, hsv.hue, TOLERANCE)
            assertEquals(1f, hsv.saturation, TOLERANCE)
            assertEquals(1f, hsv.value, TOLERANCE)
            assertEquals(packed.toInt(), hsv.toColor(255).toPackedRgba8888())
        }
    }

    @Test
    fun `numeric colours derive picker coordinates without losing RGBA8`() {
        val channels = listOf(0, 1, 17, 63, 127, 128, 193, 254, 255)
        channels.forEach { red ->
            channels.forEach { green ->
                channels.forEach { blue -> assertRoundTrip(red, green, blue) }
            }
        }
    }

    @Test
    fun `gray retains hue and black retains hue and saturation`() {
        val previous = PaletteHsvColor(213f, 0.73f, 0.8f)
        val gray = PaletteHsvColor.fromColor(PixelColor.fromPackedRgba8888(0x808080FF.toInt()), previous)
        assertEquals(213f, gray.hue)
        assertEquals(0f, gray.saturation)
        val black = PaletteHsvColor.fromColor(PixelColor.blank, previous)
        assertEquals(213f, black.hue)
        assertEquals(0.73f, black.saturation)
        assertEquals(0f, black.value)
    }

    @Test
    fun `opacity changes preserve exact hidden RGB including alpha zero`() {
        val color = PixelColor.fromPackedRgba8888(0x13A6EF00)
        assertEquals(0x13A6EF00, PaletteHsvColor.withAlpha(color, 0f).toPackedRgba8888())
        assertEquals(0x13A6EF80, PaletteHsvColor.withAlpha(color, 0.5f).toPackedRgba8888())
        assertEquals(0x13A6EFFF, PaletteHsvColor.withAlpha(color, 1f).toPackedRgba8888())
    }

    @Test
    fun `picker echo retains coordinates including the right hue endpoint and numeric input relocates them`() {
        val red = PixelColor.fromPackedRgba8888(0xFF0000FF.toInt())
        val coordinates = PalettePickerCoordinates(red)
        val endpoint = PaletteHsvColor(360f, 1f, 1f)
        val echoed = coordinates.colorAt(endpoint, red)
        coordinates.synchronize(echoed)
        assertEquals(endpoint, coordinates.hsv)
        val blue = PixelColor.fromPackedRgba8888(0x0000FFFF)
        coordinates.synchronize(blue)
        assertEquals(PaletteHsvColor(240f, 1f, 1f), coordinates.hsv)
    }

    @Test
    fun `SV updates keep starting hue across quantized preview echoes without recomposition`() {
        val start = PixelColor.fromPackedRgba8888(0x2000DFFF)
        val coordinates = PalettePickerCoordinates(start)
        val first = coordinates.colorAt(coordinates.current(start).copy(saturation = 0.2f, value = 0.8f), start)
        val second =
            coordinates.colorAt(
                coordinates.current(first, editing = true).copy(saturation = 0.4f, value = 0.6f),
                first,
            )
        // The model still holds the first RGB while local mechanics already represent the second position.
        coordinates.synchronize(first, editing = true)
        val final =
            coordinates.colorAt(
                coordinates.current(first, editing = true).copy(saturation = 0.8f, value = 0.7f),
                second,
            )
        assertEquals(PixelColor.fromPackedRgba8888(0xA9A3CCFF.toInt()), first)
        assertEquals(PixelColor.fromPackedRgba8888(0x655C99FF), second)
        assertEquals(PixelColor.fromPackedRgba8888(0x3824B3FF), final)
        assertEquals(PaletteHsvColor.fromColor(start).hue, coordinates.hsv.hue)
        coordinates.synchronize(final)
        assertEquals(PaletteHsvColor.fromColor(start).hue, coordinates.hsv.hue)
    }

    private fun assertRoundTrip(
        red: Int,
        green: Int,
        blue: Int,
    ) {
        val packed = (red shl 24) or (green shl 16) or (blue shl 8)
        val color = PixelColor.fromPackedRgba8888(packed)
        assertEquals(color, PaletteHsvColor.fromColor(color).toColor(0))
    }

    private companion object {
        const val TOLERANCE: Float = 0.001f
    }
}
