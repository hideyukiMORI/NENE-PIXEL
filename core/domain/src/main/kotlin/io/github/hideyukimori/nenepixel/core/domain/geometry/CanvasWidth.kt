package io.github.hideyukimori.nenepixel.core.domain.geometry

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
            if (value <= 0) {
                rejected(DomainValueRejection.NonPositiveCanvasWidth(value))
            } else {
                created(CanvasWidth(value))
            }
    }
}
