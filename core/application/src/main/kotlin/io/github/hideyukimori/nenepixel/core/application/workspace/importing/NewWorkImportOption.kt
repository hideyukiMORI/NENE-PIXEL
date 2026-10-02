package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan

/** Opening a picked PNG as a new work, or the application-owned reason it is not available (ADR 0033). */
public sealed interface NewWorkImportOption {
    /** The form can be chosen; executing [plan] opens the new work. */
    public data class Available internal constructor(
        public val plan: NewWorkImportPlan,
    ) : NewWorkImportOption

    /** A side of the picture is above the canvas limit of a work. */
    public data object AboveCanvasLimit : NewWorkImportOption

    /**
     * The picture has more distinct colours than a palette holds. The number is not repeated here: it is
     * [RasterImportFacts.colorCount] of the same pending import, kept in one place.
     */
    public data object TooManyColors : NewWorkImportOption

    /** The picture has no pixel that is not transparent. */
    public data object NothingToImport : NewWorkImportOption
}
