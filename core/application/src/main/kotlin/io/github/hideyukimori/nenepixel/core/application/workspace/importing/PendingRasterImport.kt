package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
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
    public val target: LayerImportTarget,
    private val forms: RasterImportForms,
) {
    public val appending: RasterImportOption get() = forms.appending
    public val converting: RasterImportOption get() = forms.converting
    public val newWork: NewWorkImportOption get() = forms.newWork

    override fun toString(): String = "PendingRasterImport(facts=$facts)"

    public companion object {
        /**
         * Plans layer forms against the pick-begin [target]'s captured canvas and palette.
         * The new-work form depends on the raster alone.
         */
        internal fun planned(
            raster: ImportRaster,
            target: LayerImportTarget,
        ): PendingRasterImport =
            PendingRasterImport(
                RasterImportFacts(raster.width, raster.height, RasterImportPlanner.colorCount(raster)),
                target,
                RasterImportForms(
                    option(
                        RasterImportPlanner.appending(
                            raster,
                            target.admission.document.size,
                            target.admission.document.definition,
                        ),
                    ),
                    option(
                        RasterImportPlanner.converting(
                            raster,
                            target.admission.document.size,
                            target.admission.document.definition,
                        ),
                    ),
                    option(RasterImportPlanner.newWork(raster)),
                ),
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
