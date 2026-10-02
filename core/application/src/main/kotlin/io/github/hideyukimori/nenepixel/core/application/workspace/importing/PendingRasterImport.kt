package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LayerImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.NewWorkImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportPlanner

/**
 * A picked PNG waiting for the user to choose how it is imported (ADR 0033): its [facts] and, for each of the
 * three forms (adding colours as a layer, converting colours as a layer, and opening as a new work), its plan
 * or why it is not available. The raster itself is not retained. Session-only, undo-neutral and
 * dirty-neutral. Equality is identity: two pending imports are the same only when they are the same instance.
 */
public class PendingRasterImport internal constructor(
    public val facts: RasterImportFacts,
    public val appending: RasterImportOption,
    public val converting: RasterImportOption,
    public val newWork: NewWorkImportOption,
) {
    override fun toString(): String = "PendingRasterImport(facts=$facts)"

    public companion object {
        /**
         * Plans the three forms once: the layer forms against the installed document's [canvas] and palette
         * [definition], and the new work from the raster alone.
         */
        internal fun planned(
            raster: ImportRaster,
            canvas: CanvasSize,
            definition: PaletteDefinition,
        ): PendingRasterImport =
            PendingRasterImport(
                RasterImportFacts(raster.width, raster.height, RasterImportPlanner.colorCount(raster)),
                option(RasterImportPlanner.appending(raster, canvas, definition)),
                option(RasterImportPlanner.converting(raster, canvas, definition)),
                option(RasterImportPlanner.newWork(raster)),
            )

        private fun option(result: LayerImportResult): RasterImportOption =
            when (result) {
                is LayerImportResult.Planned -> RasterImportOption.Available(result.plan)
                LayerImportResult.NothingToImport -> RasterImportOption.NothingToImport
            }

        private fun option(result: NewWorkImportResult): NewWorkImportOption =
            when (result) {
                is NewWorkImportResult.Planned -> NewWorkImportOption.Available(result.plan)
                NewWorkImportResult.AboveCanvasLimit -> NewWorkImportOption.AboveCanvasLimit
                NewWorkImportResult.TooManyColors -> NewWorkImportOption.TooManyColors
                NewWorkImportResult.NothingToImport -> NewWorkImportOption.NothingToImport
            }
    }
}
