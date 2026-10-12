package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
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
        definition: PaletteDefinition,
        session: PaletteEditSession?,
    ): DocumentCompositeImage {
        if (source === document && sourceDefinition === definition && sourceSession === session) {
            return requireNotNull(image)
        }
        val table =
            if (session != null && document.definition == session.origin) {
                tableDerivationCount++
                PaletteDraftComposite.colorTable(session)
            } else {
                definition
            }
        val compatible = session != null && document.definition == session.origin
        if (compatible) renderDraft(document, table) else renderCommitted(document, table)
        source = document
        sourceDefinition = definition
        sourceSession = session
        effective = table
        return requireNotNull(image)
    }

    private fun renderDraft(
        document: DocumentState,
        table: PaletteDefinition,
    ) {
        if (source !== document || prepared == null || effective?.palette?.entryCount != table.palette.entryCount) {
            prepared = prepare(document, table)
            preparationCount++
        }
        val next = requireNotNull(prepared).render(table)
        if (raster !== next && !sameEffectiveTable(document, table)) image = DocumentCompositeImage(next)
        raster = next
    }

    private fun renderCommitted(
        document: DocumentState,
        table: PaletteDefinition,
    ) {
        prepared = null
        raster = null
        if (!sameEffectiveTable(document, table)) {
            image =
                when (val result = DocumentComposite.render(document, table)) {
                    is DocumentCompositeRenderResult.Rendered -> result.image
                    DocumentCompositeRenderResult.IndexOutsidePalette -> error("Rendered index is outside the palette")
                }
        }
    }

    private fun sameEffectiveTable(
        document: DocumentState,
        table: PaletteDefinition,
    ): Boolean = source === document && effective?.palette == table.palette

    private fun prepare(
        document: DocumentState,
        definition: PaletteDefinition,
    ): PaletteComposite =
        when (val result = preparePaletteComposite(document.size, document.layers, definition)) {
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
