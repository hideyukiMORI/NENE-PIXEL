package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class CanvasWidth private constructor(
    public val value: Int,
) {
    public companion object {
        public fun create(value: Int): DomainValueResult<CanvasWidth> =
            when {
                value <= 0 -> {
                    rejected(DomainValueRejection.NonPositiveCanvasWidth(value))
                }

                value > PixelLimits.MAX_CANVAS_AXIS -> {
                    rejected(
                        DomainValueRejection.CanvasWidthAboveSupportedMaximum(
                            value,
                            PixelLimits.MAX_CANVAS_AXIS,
                        ),
                    )
                }

                else -> {
                    created(CanvasWidth(value))
                }
            }
    }
}
