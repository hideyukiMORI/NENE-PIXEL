package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class DocumentId private constructor(
    public val value: String,
) {
    public companion object {
        private const val REQUIRED_LENGTH: Int = 32

        public fun create(value: String): DomainValueResult<DocumentId> {
            if (value.length != REQUIRED_LENGTH) {
                return rejected(DomainValueRejection.InvalidDocumentIdLength(value.length))
            }
            val invalidIndex = value.indexOfFirst { character -> !character.isLowercaseHexadecimal() }
            return if (invalidIndex >= 0) {
                rejected(DomainValueRejection.InvalidDocumentIdCharacter(invalidIndex, value[invalidIndex]))
            } else {
                created(DocumentId(value))
            }
        }

        private fun Char.isLowercaseHexadecimal(): Boolean = this in '0'..'9' || this in 'a'..'f'
    }
}
