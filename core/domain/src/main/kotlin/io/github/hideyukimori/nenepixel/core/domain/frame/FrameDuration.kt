package io.github.hideyukimori.nenepixel.core.domain.frame

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class FrameDuration private constructor(
    public val value: Int,
) {
    public companion object {
        public const val MIN_MILLISECONDS: Int = 10
        public const val MAX_MILLISECONDS: Int = 60_000
        public val default: FrameDuration = FrameDuration(100)

        public fun create(value: Int): DomainValueResult<FrameDuration> =
            if (value in MIN_MILLISECONDS..MAX_MILLISECONDS) {
                created(FrameDuration(value))
            } else {
                rejected(DomainValueRejection.InvalidFrameDuration(value))
            }
    }
}
