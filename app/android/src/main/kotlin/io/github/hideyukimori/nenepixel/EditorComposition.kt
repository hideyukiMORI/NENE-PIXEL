package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal fun createEditorRuntime(): EditorRuntime =
    EditorRuntime.create(
        initialCanvas =
            CanvasSize.create(
                CanvasWidth.create(INITIAL_CANVAS_EDGE).requiredValue(),
                CanvasHeight.create(INITIAL_CANVAS_EDGE).requiredValue(),
            ),
        initialActiveColor =
            PixelColor.create(
                red = ColorChannel.create(CHANNEL_MAX).requiredValue(),
                green = ColorChannel.create(CHANNEL_MIN).requiredValue(),
                blue = ColorChannel.create(CHANNEL_MIN).requiredValue(),
                alpha = ColorChannel.create(CHANNEL_MAX).requiredValue(),
            ),
        documentIdSource = UuidDocumentIdSource(),
    )

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Application composition is invalid: $rejection")
    }

private const val INITIAL_CANVAS_EDGE: Int = 16
private const val CHANNEL_MIN: Int = 0
private const val CHANNEL_MAX: Int = 255
