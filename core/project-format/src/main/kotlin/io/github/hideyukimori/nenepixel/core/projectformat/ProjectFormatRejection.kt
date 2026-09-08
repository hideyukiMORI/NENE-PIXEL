package io.github.hideyukimori.nenepixel.core.projectformat

public sealed interface ProjectFormatRejection {
    public data class ResourceLimitExceeded internal constructor(
        public val actualByteCount: Int,
        public val maximumByteCount: Int,
    ) : ProjectFormatRejection

    public data class Truncated internal constructor(
        public val actualByteCount: Int,
        public val requiredByteCount: Int,
    ) : ProjectFormatRejection

    public data object InvalidMagic : ProjectFormatRejection

    public data class UnsupportedVersion internal constructor(
        public val actualVersion: ProjectFormatVersion,
    ) : ProjectFormatRejection

    public data object InvalidCanvas : ProjectFormatRejection

    public data object InvalidRevision : ProjectFormatRejection

    public data class TrailingData internal constructor(
        public val actualByteCount: Int,
        public val expectedByteCount: Int,
    ) : ProjectFormatRejection

    public data class ChecksumMismatch internal constructor(
        public val computedChecksum: UInt,
        public val storedChecksum: UInt,
    ) : ProjectFormatRejection
}
