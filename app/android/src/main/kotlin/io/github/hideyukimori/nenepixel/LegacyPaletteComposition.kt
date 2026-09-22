package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.LegacyPalettePresets
import kotlin.math.roundToInt

internal fun createLegacyPalettePresets(): LegacyPalettePresets =
    LegacyPalettePresets(
        dusk = preset(listOf(PixelColor.blank) + DUSK_RGB.map(::opaqueColor), 0),
        grayscale = preset(grayscaleColors(), GRAY_OPAQUE_COUNT),
        swatches = preset(swatchColors(), 0),
    )

private fun grayscaleColors(): List<PixelColor> =
    List(GRAY_OPAQUE_COUNT) { index ->
        opaqueGray((index * CHANNEL_MAX.toDouble() / (GRAY_OPAQUE_COUNT - 1)).roundToInt())
    } + PixelColor.blank

private fun swatchColors(): List<PixelColor> =
    buildList {
        addAll(cubeColors())
        var gray = FIRST_EXTRA_GRAY
        while (size < SWATCH_COUNT) {
            if (gray !in CUBE_LEVELS) add(opaqueGray(gray))
            gray += EXTRA_GRAY_STEP
        }
    }

private fun cubeColors(): List<PixelColor> =
    buildList {
        for (red in CUBE_LEVELS) {
            for (green in CUBE_LEVELS) {
                for (blue in CUBE_LEVELS) {
                    add(opaqueColor((red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue))
                }
            }
        }
    }

private fun opaqueGray(value: Int): PixelColor = opaqueColor((value shl RED_SHIFT) or (value shl GREEN_SHIFT) or value)

private fun opaqueColor(rgb: Int): PixelColor = PixelColor.fromPackedRgba8888((rgb shl ALPHA_SHIFT) or CHANNEL_MAX)

private fun preset(
    colors: List<PixelColor>,
    defaultIndex: Int,
): PaletteDefinition =
    PaletteDefinition
        .create(
            Palette.create(colors).requiredPresetValue(),
            PaletteIndex.create(defaultIndex).requiredPresetValue(),
        ).requiredPresetValue()

private fun <T> DomainValueResult<T>.requiredPresetValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid fixed conversion preset: $rejection")
    }

private val DUSK_RGB =
    listOf(
        0x1B1029,
        0x3A1F4D,
        0x5E2750,
        0x8C3B5E,
        0xC25B5B,
        0xE8845A,
        0xF5B461,
        0xFBE3A1,
        0xF6F1E9,
        0x9DC4B6,
        0x4F8A7B,
        0x2E5B5A,
        0x1F3A4A,
        0x5B6E9B,
        0xA7A3C9,
    )
private val CUBE_LEVELS = listOf(0, 51, 102, 153, 204, 255)
private const val CHANNEL_MAX = 255
private const val GRAY_OPAQUE_COUNT = 31
private const val SWATCH_COUNT = 256
private const val FIRST_EXTRA_GRAY = 8
private const val EXTRA_GRAY_STEP = 6
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val ALPHA_SHIFT = 8
