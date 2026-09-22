package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource

public sealed interface LegacyImportResult {
    public data class Lossless internal constructor(
        public val document: DocumentState,
    ) : LegacyImportResult

    public data class ConversionRequired internal constructor(
        public val source: LegacyRgbaSource,
        public val distinctColorCount: Int,
    ) : LegacyImportResult
}
