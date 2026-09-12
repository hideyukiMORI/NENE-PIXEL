package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

public sealed interface PaletteJsonRejection {
    public data class ResourceLimitExceeded internal constructor(
        public val actualByteCount: Int,
        public val maximumByteCount: Int,
    ) : PaletteJsonRejection

    public data object InvalidUtf8 : PaletteJsonRejection

    public data class InvalidJson internal constructor(
        public val characterOffset: Int,
    ) : PaletteJsonRejection

    public data class IntegerOverflow internal constructor(
        public val characterOffset: Int,
    ) : PaletteJsonRejection

    public data object UnknownField : PaletteJsonRejection

    public data object DuplicateField : PaletteJsonRejection

    public data object MissingField : PaletteJsonRejection

    public data object UnsupportedFormat : PaletteJsonRejection

    public data object UnsupportedVersion : PaletteJsonRejection

    public data class InvalidColor internal constructor(
        public val entryPosition: Int,
    ) : PaletteJsonRejection

    public data object TooManyColors : PaletteJsonRejection

    public data class InvalidDefinition internal constructor(
        public val rejection: DomainValueRejection,
    ) : PaletteJsonRejection
}
