package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal fun createEditorRuntime(): EditorRuntime =
    EditorRuntime.create(
        initialCanvas =
            CanvasSize.create(
                CanvasWidth.create(INITIAL_CANVAS_EDGE).requiredValue(),
                CanvasHeight.create(INITIAL_CANVAS_EDGE).requiredValue(),
            ),
        palette = createMvpPalette(),
        documentIdSource = UuidDocumentIdSource(),
    )

private fun createMvpPalette(): Palette =
    Palette
        .create(
            listOf(
                color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN),
                color(CHANNEL_MIN, CHANNEL_MIN, CHANNEL_MIN),
                color(CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX),
                color(CHANNEL_MIN, CHANNEL_MAX, CHANNEL_MIN),
                color(CHANNEL_MIN, CHANNEL_MIN, CHANNEL_MAX),
                color(CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MIN),
                color(CHANNEL_MIN, CHANNEL_MAX, CHANNEL_MAX),
                color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MAX),
            ),
        ).requiredValue()

private fun color(
    red: Int,
    green: Int,
    blue: Int,
): PixelColor =
    PixelColor.create(
        red = ColorChannel.create(red).requiredValue(),
        green = ColorChannel.create(green).requiredValue(),
        blue = ColorChannel.create(blue).requiredValue(),
        alpha = ColorChannel.create(CHANNEL_MAX).requiredValue(),
    )

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Application composition is invalid: $rejection")
    }

private const val INITIAL_CANVAS_EDGE: Int = 16
private const val CHANNEL_MIN: Int = 0
private const val CHANNEL_MAX: Int = 255
