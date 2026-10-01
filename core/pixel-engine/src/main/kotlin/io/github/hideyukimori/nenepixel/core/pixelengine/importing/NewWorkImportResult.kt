package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan

/** The outcome of planning a picture as a new work (ADR 0033). */
public sealed interface NewWorkImportResult {
    public data class Planned internal constructor(
        public val plan: NewWorkImportPlan,
    ) : NewWorkImportResult

    /** A side of the picture is above the canvas limit of a work. */
    public data object AboveCanvasLimit : NewWorkImportResult

    /** The picture has more distinct colours than a palette holds; [colorCount] is their number. */
    public data class TooManyColors internal constructor(
        public val colorCount: Int,
    ) : NewWorkImportResult

    /** The picture has no pixel that is not transparent. */
    public data object NothingToImport : NewWorkImportResult
}
