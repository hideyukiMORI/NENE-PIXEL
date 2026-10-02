package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LayerImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportPlanner

/**
 * A picked PNG waiting for the user to choose how it is imported (ADR 0033): its [facts] and, for each layer
 * form, its plan or why it is not available. The raster itself is not retained. Session-only, undo-neutral and
 * dirty-neutral. Equality is identity: two pending imports are the same only when they are the same instance.
 */
public class PendingRasterImport internal constructor(
    public val facts: RasterImportFacts,
    public val appending: RasterImportOption,
    public val converting: RasterImportOption,
) {
    override fun toString(): String = "PendingRasterImport(facts=$facts)"

    public companion object {
        /** Plans both layer forms once against the installed document's [canvas] and palette [definition]. */
        internal fun planned(
            raster: ImportRaster,
            canvas: CanvasSize,
            definition: PaletteDefinition,
        ): PendingRasterImport =
            PendingRasterImport(
                RasterImportFacts(raster.width, raster.height, RasterImportPlanner.colorCount(raster)),
                option(RasterImportPlanner.appending(raster, canvas, definition)),
                option(RasterImportPlanner.converting(raster, canvas, definition)),
            )

        private fun option(result: LayerImportResult): RasterImportOption =
            when (result) {
                is LayerImportResult.Planned -> RasterImportOption.Available(result.plan)
                LayerImportResult.NothingToImport -> RasterImportOption.NothingToImport
            }
    }
}
