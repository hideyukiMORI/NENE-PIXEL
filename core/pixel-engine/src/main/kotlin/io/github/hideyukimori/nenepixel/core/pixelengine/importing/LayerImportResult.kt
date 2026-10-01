package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan

/** The outcome of planning a picture as a new layer (ADR 0033). */
public sealed interface LayerImportResult {
    public data class Planned internal constructor(
        public val plan: LayerImportPlan,
    ) : LayerImportResult

    /** No pixel that is not transparent lies on the canvas. */
    public data object NothingToImport : LayerImportResult
}
