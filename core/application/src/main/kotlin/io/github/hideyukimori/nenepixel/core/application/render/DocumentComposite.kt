package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.compositeLayers

/**
 * The application entry to the single Composite of ADR 0030 for callers that cannot see the
 * pixel engine (display, previews and PNG export).
 */
public object DocumentComposite {
    /** Composites [document] with its own palette; the document invariants rule out rejection. */
    public fun render(document: DocumentState): DocumentCompositeImage =
        when (val result = compositeLayers(document.size, document.layers, document.definition)) {
            is CompositeResult.Composited -> result.image()
            is CompositeResult.Rejected -> error("Document composite invariant was rejected: ${result.rejection}")
        }

    /** Composites the document's layers with a draft [definition], as in the palette editor preview. */
    public fun render(
        document: DocumentState,
        definition: PaletteDefinition,
    ): DocumentCompositeRenderResult =
        when (val result = compositeLayers(document.size, document.layers, definition)) {
            is CompositeResult.Composited -> DocumentCompositeRenderResult.Rendered(result.image())
            is CompositeResult.Rejected -> draftRejection(result.rejection)
        }

    private fun draftRejection(rejection: CompositeRejection): DocumentCompositeRenderResult =
        when (rejection) {
            is CompositeRejection.IndexOutsidePalette -> DocumentCompositeRenderResult.IndexOutsidePalette

            CompositeRejection.NoLayers,
            is CompositeRejection.LayerSizeMismatch,
            -> error("Document composite invariant was rejected: $rejection")
        }

    private fun CompositeResult.Composited.image(): DocumentCompositeImage =
        DocumentCompositeImage(raster.size, raster.copyPackedRgba8888())
}
