package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

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

    public data class InvalidPaletteEntryCount internal constructor(
        public val actualCount: Int,
        public val minimum: Int,
        public val maximum: Int,
    ) : ProjectFormatRejection

    public data class DefaultIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
        public val entryCount: Int,
    ) : ProjectFormatRejection

    public data class PixelIndexOutsidePalette internal constructor(
        public val position: PixelPosition,
        public val index: PaletteIndex,
        public val entryCount: Int,
    ) : ProjectFormatRejection
}
