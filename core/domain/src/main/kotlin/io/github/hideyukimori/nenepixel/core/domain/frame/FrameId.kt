package io.github.hideyukimori.nenepixel.core.domain.frame

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class FrameId private constructor(
    public val value: Int,
) {
    public fun next(): DomainValueResult<FrameId> =
        if (value == Int.MAX_VALUE) rejected(DomainValueRejection.FrameIdOverflow) else created(FrameId(value + 1))

    public companion object {
        public fun first(): FrameId = FrameId(1)

        public fun create(value: Int): DomainValueResult<FrameId> =
            if (value < 1) rejected(DomainValueRejection.InvalidFrameId(value)) else created(FrameId(value))
    }
}
