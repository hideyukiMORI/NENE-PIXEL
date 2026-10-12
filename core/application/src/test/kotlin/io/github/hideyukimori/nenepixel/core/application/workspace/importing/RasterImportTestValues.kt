package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Pure-plan fixtures capture one canonical single-frame source; runtime tests use their real pick target. */
internal object RasterImportTestValues {
    fun planned(
        raster: ImportRaster,
        canvas: CanvasSize,
        definition: PaletteDefinition,
    ): PendingRasterImport {
        val result =
            DocumentState.createSingleLayer(
                defaultDocumentId,
                Revision.initial(),
                definition,
                PixelSnapshot.createEmpty(canvas),
            )
        val document =
            when (result) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Invalid import fixture: ${result.rejection}")
            }
        val target =
            LayerImportTarget.capture(
                CommandGateway.create(document).captureSource(),
                document.frames.single().id,
                document.frames
                    .single()
                    .layers
                    .single()
                    .id,
            )
        return PendingRasterImport.planned(raster, target)
    }
}
