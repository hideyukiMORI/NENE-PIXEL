package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * The palette editor's text forms of one color: `#RRGGBBAA` and decimal channels 0–255. Parsing returns `null` for any
 * input outside those forms; the domain `PixelColor` stays the only color value.
 */
internal object PaletteHexColor {
    private const val HEX_DIGITS: Int = 8
    private const val HEX_RADIX: Int = 16
    private const val CHANNEL_WIDTH: Int = 2
    private const val PREFIX: Char = '#'
    private val hexPattern: Regex = Regex("[0-9A-Fa-f]{$HEX_DIGITS}")
    private val channelPattern: Regex = Regex("[0-9]{1,3}")

    fun format(color: PixelColor): String =
        channels(color).joinToString(separator = "", prefix = PREFIX.toString()) { channel ->
            channel.value
                .toInt()
                .toString(HEX_RADIX)
                .uppercase()
                .padStart(CHANNEL_WIDTH, '0')
        }

    fun parse(text: String): PixelColor? {
        val digits = text.trim().removePrefix(PREFIX.toString())
        if (!hexPattern.matches(digits)) return null
        val values = digits.chunked(CHANNEL_WIDTH).map { it.toInt(HEX_RADIX) }
        return colorOf(values)
    }

    fun parseChannel(text: String): ColorChannel? {
        val digits = text.trim()
        if (!channelPattern.matches(digits)) return null
        return when (val channel = ColorChannel.create(digits.toInt())) {
            is DomainValueResult.Created -> channel.value
            is DomainValueResult.Rejected -> null
        }
    }

    fun channels(color: PixelColor): List<ColorChannel> = listOf(color.red, color.green, color.blue, color.alpha)

    fun withChannel(
        color: PixelColor,
        position: Int,
        channel: ColorChannel,
    ): PixelColor {
        val next = channels(color).toMutableList()
        next[position] = channel
        return colorOfChannels(next)
    }

    private fun colorOf(values: List<Int>): PixelColor? {
        val channels =
            values
                .map { ColorChannel.create(it) }
                .filterIsInstance<DomainValueResult.Created<ColorChannel>>()
                .map { it.value }
        return if (channels.size == values.size) colorOfChannels(channels) else null
    }

    private fun colorOfChannels(channels: List<ColorChannel>): PixelColor {
        val (red, green, blue) = channels
        return PixelColor.create(red, green, blue, channels.last())
    }
}
