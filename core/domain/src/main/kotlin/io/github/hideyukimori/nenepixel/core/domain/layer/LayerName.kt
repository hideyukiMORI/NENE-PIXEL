package io.github.hideyukimori.nenepixel.core.domain.layer

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class LayerName private constructor(
    public val value: String,
) {
    public companion object {
        public val empty: LayerName = LayerName("")

        public fun create(value: String): DomainValueResult<LayerName> =
            when (val rejection = rejectionFor(value)) {
                null -> created(LayerName(value))
                else -> rejected(rejection)
            }

        private fun rejectionFor(value: String): DomainValueRejection? =
            invalidSurrogate(value) ?: tooLong(value) ?: controlCharacter(value)

        private fun invalidSurrogate(value: String): DomainValueRejection? =
            value.indices
                .firstOrNull { index -> value.isUnpairedSurrogateAt(index) }
                ?.let { index -> DomainValueRejection.LayerNameInvalidSurrogate(index) }

        private fun tooLong(value: String): DomainValueRejection? =
            value
                .codePointCount(0, value.length)
                .takeIf { count -> count > LayerLimits.MAX_NAME_CODE_POINTS }
                ?.let { count -> DomainValueRejection.LayerNameTooLong(count) }

        private fun controlCharacter(value: String): DomainValueRejection? =
            value
                .codePoints()
                .toArray()
                .indexOfFirst { codePoint -> Character.isISOControl(codePoint) }
                .takeIf { index -> index >= 0 }
                ?.let { index -> DomainValueRejection.LayerNameControlCharacter(index) }

        private fun String.isUnpairedSurrogateAt(index: Int): Boolean =
            when {
                this[index].isHighSurrogate() -> index + 1 >= length || !this[index + 1].isLowSurrogate()
                this[index].isLowSurrogate() -> index == 0 || !this[index - 1].isHighSurrogate()
                else -> false
            }
    }
}
