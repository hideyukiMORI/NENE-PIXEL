package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan

/** One way of importing a picked PNG as a layer, or the application-owned reason it is not available (ADR 0033). */
public sealed interface RasterImportOption {
    /** The form can be chosen; executing [plan] imports the layer. */
    public data class Available internal constructor(
        public val plan: LayerImportPlan,
    ) : RasterImportOption

    /** No pixel that is not transparent lies on the canvas. */
    public data object NothingToImport : RasterImportOption
}
