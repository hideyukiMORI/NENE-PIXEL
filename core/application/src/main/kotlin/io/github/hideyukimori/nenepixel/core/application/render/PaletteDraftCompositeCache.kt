package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.CompositeRaster
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PaletteComposite
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PaletteCompositeResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.preparePaletteComposite

/**
 * Single-consumer disposable display cache. Session metadata is not an image identity: equal
 * origin-sized tables reuse the image. Document replacement or incompatible tables reprepare.
 */
public class PaletteDraftCompositeCache {
    private var source: DocumentState? = null
    private var sourceFrame: FrameId? = null
    private var sourceDefinition: PaletteDefinition? = null
    private var sourceSession: PaletteEditSession? = null
    private var effective: PaletteDefinition? = null
    private var prepared: PaletteComposite? = null
    private var raster: CompositeRaster? = null
    private var image: DocumentCompositeImage? = null

    internal var preparationCount: Int = 0
        private set

    internal var tableDerivationCount: Int = 0
        private set

    /** Uses the session's canonical remap table; an incompatible session falls back to [definition]. */
    public fun render(
        document: DocumentState,
        frameId: FrameId,
        definition: PaletteDefinition,
        session: PaletteEditSession?,
    ): DocumentCompositeImage {
        if (sameSourceFrame(document, frameId) && sourceDefinition === definition &&
            sourceSession === session
        ) {
            return requireNotNull(image)
        }
        requireNotNull(document.frameById(frameId)) { "Palette display frame is missing" }
        val table =
            if (session != null && document.definition == session.origin) {
                tableDerivationCount++
                PaletteDraftComposite.colorTable(session)
            } else {
                definition
            }
        val compatible = session != null && document.definition == session.origin
        if (compatible) renderDraft(document, frameId, table) else renderCommitted(document, frameId, table)
        source = document
        sourceFrame = frameId
        sourceDefinition = definition
        sourceSession = session
        effective = table
        return requireNotNull(image)
    }

    private fun renderDraft(
        document: DocumentState,
        frameId: FrameId,
        table: PaletteDefinition,
    ) {
        if (!sameSourceFrame(document, frameId) || prepared == null ||
            effective?.palette?.entryCount != table.palette.entryCount
        ) {
            prepared = prepare(document, frameId, table)
            preparationCount++
        }
        val next = requireNotNull(prepared).render(table)
        if (raster !== next && !sameEffectiveTable(document, frameId, table)) image = DocumentCompositeImage(next)
        raster = next
    }

    private fun renderCommitted(
        document: DocumentState,
        frameId: FrameId,
        table: PaletteDefinition,
    ) {
        prepared = null
        raster = null
        if (!sameEffectiveTable(document, frameId, table)) {
            image =
                when (val result = DocumentComposite.render(document, frameId, table)) {
                    is DocumentCompositeRenderResult.Rendered -> result.image
                    is DocumentCompositeRenderResult.FrameNotFound -> error("Rendered frame is missing")
                    DocumentCompositeRenderResult.IndexOutsidePalette -> error("Rendered index is outside the palette")
                }
        }
    }

    private fun sameSourceFrame(
        document: DocumentState,
        frameId: FrameId,
    ): Boolean = source === document && sourceFrame == frameId

    private fun sameEffectiveTable(
        document: DocumentState,
        frameId: FrameId,
        table: PaletteDefinition,
    ): Boolean = sameSourceFrame(document, frameId) && effective?.palette == table.palette

    private fun prepare(
        document: DocumentState,
        frameId: FrameId,
        definition: PaletteDefinition,
    ): PaletteComposite =
        when (
            val result =
                preparePaletteComposite(document.size, requireNotNull(document.frameById(frameId)).layers, definition)
        ) {
            is PaletteCompositeResult.Prepared -> {
                result.composite
            }

            is PaletteCompositeResult.Rejected -> {
                error(
                    "Document composite invariant was rejected: ${result.rejection}",
                )
            }
        }
}
