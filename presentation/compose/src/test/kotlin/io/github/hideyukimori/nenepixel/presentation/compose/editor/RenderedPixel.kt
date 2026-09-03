package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.Color
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal data class RenderedPixel(
    val position: PixelPosition,
    val color: Color,
)

internal fun PixelSnapshot.toRenderedPixels(): List<RenderedPixel> =
    List(size.pixelCount.toInt()) { index ->
        val position = pixelPosition(index % size.width.value, index / size.width.value)
        val color =
            when (val result = colorAt(position)) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Validated legacy projection was rejected: ${result.rejection}")
            }
        RenderedPixel(position, color.toComposeColor())
    }
