package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class CanvasHeight private constructor(
    public val value: Int,
) {
    public companion object {
        public fun create(value: Int): DomainValueResult<CanvasHeight> =
            when {
                value < PixelLimits.MIN_CANVAS_AXIS -> {
                    rejected(DomainValueRejection.NonPositiveCanvasHeight(value))
                }

                value > PixelLimits.MAX_CANVAS_AXIS -> {
                    rejected(
                        DomainValueRejection.CanvasHeightAboveSupportedMaximum(
                            value,
                            PixelLimits.MAX_CANVAS_AXIS,
                        ),
                    )
                }

                else -> {
                    created(CanvasHeight(value))
                }
            }
    }
}
