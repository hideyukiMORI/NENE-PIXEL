package io.github.hideyukimori.nenepixel.core.domain.color

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class ColorChannel private constructor(
    public val value: UByte,
) {
    public companion object {
        public fun create(value: Int): DomainValueResult<ColorChannel> =
            if (value < UByte.MIN_VALUE.toInt() || value > UByte.MAX_VALUE.toInt()) {
                rejected(DomainValueRejection.ColorChannelOutsideRange(value))
            } else {
                created(ColorChannel(value.toUByte()))
            }
    }
}
