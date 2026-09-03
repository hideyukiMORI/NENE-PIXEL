package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public data class NewDocumentRequest private constructor(
    public val canvas: CanvasSize,
) {
    public companion object {
        public fun create(
            rawWidth: String,
            rawHeight: String,
        ): NewDocumentRequestResult =
            when (val width = parse(rawWidth, NewDocumentDimension.Width, CanvasWidth::create)) {
                is DimensionParseResult.Created -> createWithWidth(width.value, rawHeight)
                is DimensionParseResult.Rejected -> NewDocumentRequestResult.Rejected(width.rejection)
            }

        private fun createWithWidth(
            width: CanvasWidth,
            rawHeight: String,
        ): NewDocumentRequestResult =
            when (val height = parse(rawHeight, NewDocumentDimension.Height, CanvasHeight::create)) {
                is DimensionParseResult.Created -> {
                    NewDocumentRequestResult.Created(
                        NewDocumentRequest(
                            CanvasSize.create(width, height.value),
                        ),
                    )
                }

                is DimensionParseResult.Rejected -> {
                    NewDocumentRequestResult.Rejected(height.rejection)
                }
            }

        private fun <T> parse(
            rawValue: String,
            dimension: NewDocumentDimension,
            createDimension: (Int) -> DomainValueResult<T>,
        ): DimensionParseResult<T> {
            val normalized = rawValue.trim()
            return when {
                normalized.isEmpty() -> {
                    DimensionParseResult.Rejected(NewDocumentRejection.Required(dimension))
                }

                !DECIMAL_INTEGER.matches(normalized) -> {
                    DimensionParseResult.Rejected(NewDocumentRejection.NotDecimalInteger(dimension))
                }

                else -> {
                    parseDecimalInteger(normalized, dimension, createDimension)
                }
            }
        }

        private fun <T> parseDecimalInteger(
            normalized: String,
            dimension: NewDocumentDimension,
            createDimension: (Int) -> DomainValueResult<T>,
        ): DimensionParseResult<T> {
            val parsed = normalized.toIntOrNull()
            return if (parsed == null) {
                DimensionParseResult.Rejected(NewDocumentRejection.IntegerOverflow(dimension))
            } else {
                validateDimension(parsed, dimension, createDimension)
            }
        }

        private fun <T> validateDimension(
            parsed: Int,
            dimension: NewDocumentDimension,
            createDimension: (Int) -> DomainValueResult<T>,
        ): DimensionParseResult<T> =
            when (val result = createDimension(parsed)) {
                is DomainValueResult.Created -> {
                    DimensionParseResult.Created(result.value)
                }

                is DomainValueResult.Rejected -> {
                    DimensionParseResult.Rejected(
                        NewDocumentRejection.OutsideSupportedRange(
                            dimension,
                            parsed,
                            PixelLimits.MIN_CANVAS_AXIS,
                            PixelLimits.MAX_CANVAS_AXIS,
                        ),
                    )
                }
            }

        private val DECIMAL_INTEGER: Regex = Regex("[+-]?[0-9]+")
    }
}

private sealed interface DimensionParseResult<out T> {
    data class Created<out T>(
        val value: T,
    ) : DimensionParseResult<T>

    data class Rejected(
        val rejection: NewDocumentRejection,
    ) : DimensionParseResult<Nothing>
}
