package io.github.hideyukimori.nenepixel.core.domain.layer

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class LayerId private constructor(
    public val value: Int,
) {
    public fun next(): DomainValueResult<LayerId> =
        if (value == Int.MAX_VALUE) {
            rejected(DomainValueRejection.LayerIdOverflow)
        } else {
            created(LayerId(value + 1))
        }

    public companion object {
        private const val MINIMUM: Int = 1

        public fun first(): LayerId = LayerId(MINIMUM)

        public fun create(value: Int): DomainValueResult<LayerId> =
            if (value < MINIMUM) {
                rejected(DomainValueRejection.InvalidLayerId(value))
            } else {
                created(LayerId(value))
            }
    }
}
