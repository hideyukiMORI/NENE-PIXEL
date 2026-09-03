package io.github.hideyukimori.nenepixel.core.application.editor

public sealed interface NewDocumentRejection {
    public data class Required internal constructor(
        public val dimension: NewDocumentDimension,
    ) : NewDocumentRejection

    public data class NotDecimalInteger internal constructor(
        public val dimension: NewDocumentDimension,
    ) : NewDocumentRejection

    public data class IntegerOverflow internal constructor(
        public val dimension: NewDocumentDimension,
    ) : NewDocumentRejection

    public data class OutsideSupportedRange internal constructor(
        public val dimension: NewDocumentDimension,
        public val attemptedValue: Int,
        public val minimum: Int,
        public val maximum: Int,
    ) : NewDocumentRejection
}
