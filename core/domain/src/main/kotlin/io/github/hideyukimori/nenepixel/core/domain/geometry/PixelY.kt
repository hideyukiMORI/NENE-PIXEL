package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class PixelY private constructor(
    public val value: Int,
) {
    public companion object {
        public fun create(value: Int): DomainValueResult<PixelY> =
            if (value < 0) {
                rejected(DomainValueRejection.NegativePixelY(value))
            } else {
                created(PixelY(value))
            }
    }
}
