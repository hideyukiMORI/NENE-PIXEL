package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PixelColorComposeAdapterContractTest {
    @Test
    fun `Compose adapter preserves complete edge Cartesian RGBA in sRGB`() {
        val colors = edgeColors()

        assertEquals(EXPECTED_COLOR_COUNT, colors.size)
        colors.forEach(::assertComposeColor)
    }

    @Test
    fun `Compose adapter keeps hidden RGB distinct at alpha zero`() {
        val transparentBlack = pixelColor(RgbaChannels(0, 0, 0, 0)).toComposeColor()
        val transparentRed = pixelColor(RgbaChannels(255, 0, 0, 0)).toComposeColor()

        assertEquals(0x00000000, transparentBlack.toArgb())
        assertEquals(0x00ff0000, transparentRed.toArgb())
        assertNotEquals(transparentBlack.toArgb(), transparentRed.toArgb())
    }

    private fun assertComposeColor(channels: RgbaChannels) {
        val actual = pixelColor(channels).toComposeColor()

        assertEquals(channels.red.normalized, actual.red, CHANNEL_TOLERANCE)
        assertEquals(channels.green.normalized, actual.green, CHANNEL_TOLERANCE)
        assertEquals(channels.blue.normalized, actual.blue, CHANNEL_TOLERANCE)
        assertEquals(channels.alpha.normalized, actual.alpha, CHANNEL_TOLERANCE)
        assertEquals(ColorSpaces.Srgb, actual.colorSpace)
        assertEquals(channels.argb, actual.toArgb())
    }

    private fun pixelColor(channels: RgbaChannels): PixelColor =
        PixelColor.create(
            channel(channels.red),
            channel(channels.green),
            channel(channels.blue),
            channel(channels.alpha),
        )

    private fun channel(value: Int): ColorChannel =
        when (val result = ColorChannel.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Edge channel was rejected: ${result.rejection}")
        }

    private fun edgeColors(): List<RgbaChannels> =
        CHANNEL_EDGES.flatMap { red ->
            CHANNEL_EDGES.flatMap { green ->
                CHANNEL_EDGES.flatMap { blue ->
                    CHANNEL_EDGES.map { alpha -> RgbaChannels(red, green, blue, alpha) }
                }
            }
        }

    private data class RgbaChannels(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    ) {
        val argb: Int
            get() = (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    private val Int.normalized: Float
        get() = toFloat() / UByte.MAX_VALUE.toInt()

    private companion object {
        const val EXPECTED_COLOR_COUNT: Int = 1_296
        val CHANNEL_TOLERANCE: Float = 1.0f / (UByte.MAX_VALUE.toInt() * 2.0f)
        const val ALPHA_SHIFT: Int = 24
        const val RED_SHIFT: Int = 16
        const val GREEN_SHIFT: Int = 8
        val CHANNEL_EDGES: List<Int> = listOf(0, 1, 127, 128, 254, 255)
    }
}
