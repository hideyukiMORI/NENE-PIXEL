package io.github.hideyukimori.nenepixel.presentation.compose.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class FixedCanvasTouchTranslator private constructor() {
    fun translate(
        offset: Offset,
        surface: Size,
        canvas: CanvasSize,
    ): TouchTranslation =
        if (!offset.isInside(surface)) {
            TouchTranslation.Outside
        } else {
            mapInside(offset, surface, canvas)
        }

    private fun mapInside(
        offset: Offset,
        surface: Size,
        canvas: CanvasSize,
    ): TouchTranslation {
        val x = (offset.x / (surface.width / canvas.width.value.toFloat())).toInt()
        val y = (offset.y / (surface.height / canvas.height.value.toFloat())).toInt()
        return if (x in 0 until canvas.width.value && y in 0 until canvas.height.value) {
            TouchTranslation.Mapped(
                PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue()),
            )
        } else {
            TouchTranslation.Outside
        }
    }

    private fun Offset.isInside(surface: Size): Boolean =
        x.isFinite() &&
            y.isFinite() &&
            surface.width.isFinite() &&
            surface.height.isFinite() &&
            surface.width > 0.0f &&
            surface.height > 0.0f &&
            x >= 0.0f &&
            y >= 0.0f &&
            x < surface.width &&
            y < surface.height

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Mapped touch coordinate was rejected: $rejection")
        }

    companion object {
        fun create(): FixedCanvasTouchTranslator = FixedCanvasTouchTranslator()
    }
}
