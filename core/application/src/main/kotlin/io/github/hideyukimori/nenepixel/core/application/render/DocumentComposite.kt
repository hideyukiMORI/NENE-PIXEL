package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.compositeLayers

/**
 * The application entry to the single Composite of ADR 0030 for callers that cannot see the
 * pixel engine (display, previews and PNG export).
 */
public object DocumentComposite {
    public fun render(
        document: DocumentState,
        frameId: FrameId,
    ): DocumentCompositeRenderResult = render(document, frameId, document.definition)

    /** Arbitrary positive frame identities have one typed missing-frame outcome; never a fallback. */
    public fun render(
        document: DocumentState,
        frameId: FrameId,
        definition: PaletteDefinition,
    ): DocumentCompositeRenderResult {
        val frame = document.frameById(frameId) ?: return DocumentCompositeRenderResult.FrameNotFound(frameId)
        return when (val result = compositeLayers(document.size, frame.layers, definition)) {
            is CompositeResult.Composited -> DocumentCompositeRenderResult.Rendered(result.image())
            is CompositeResult.Rejected -> draftRejection(result.rejection)
        }
    }

    private fun draftRejection(rejection: CompositeRejection): DocumentCompositeRenderResult =
        when (rejection) {
            is CompositeRejection.IndexOutsidePalette -> DocumentCompositeRenderResult.IndexOutsidePalette

            CompositeRejection.NoLayers,
            is CompositeRejection.LayerSizeMismatch,
            -> error("Document composite invariant was rejected: $rejection")
        }

    private fun CompositeResult.Composited.image(): DocumentCompositeImage = DocumentCompositeImage(raster)
}
