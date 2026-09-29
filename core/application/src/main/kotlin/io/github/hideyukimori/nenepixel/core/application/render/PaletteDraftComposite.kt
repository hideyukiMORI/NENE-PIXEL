package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * The Composite (ADR 0030) the document would have after the palette draft is applied; nothing is changed.
 *
 * Every original palette index takes the colour of the draft entry that the session's composed remap leads to
 * (ADR 0022). The layers are untouched: the colour table has the origin's entry count and goes through the single
 * document Composite. A pending import is not shown.
 */
public object PaletteDraftComposite {
    public fun render(
        document: DocumentState,
        session: PaletteEditSession,
    ): PaletteDraftCompositeResult =
        if (document.definition != session.origin) {
            PaletteDraftCompositeResult.SourceMismatch
        } else {
            when (val result = DocumentComposite.render(document, colorTable(session))) {
                is DocumentCompositeRenderResult.Rendered -> PaletteDraftCompositeResult.Rendered(result.image)
                DocumentCompositeRenderResult.IndexOutsidePalette -> error("Draft colour table lost an origin index")
            }
        }

    /** The origin-sized table: entry `i` is the draft colour at `composedRemap().destinationAt(i)`. */
    private fun colorTable(session: PaletteEditSession): PaletteDefinition {
        val remap = session.composedRemap()
        val colors =
            session.origin.palette
                .entries()
                .map { entry -> draftColor(session, remap, entry.index) }
        return when (val palette = Palette.create(colors)) {
            is DomainValueResult.Created -> definitionOf(palette.value, session.origin.defaultIndex)
            is DomainValueResult.Rejected -> error("Draft colour table is not a palette: ${palette.rejection}")
        }
    }

    private fun draftColor(
        session: PaletteEditSession,
        remap: PaletteRemap,
        index: PaletteIndex,
    ): PixelColor {
        val destination =
            when (val result = remap.destinationAt(index)) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Draft remap misses an origin index: ${result.rejection}")
            }
        return when (val entry = session.draft.palette.entryAt(destination)) {
            is DomainValueResult.Created -> entry.value.color
            is DomainValueResult.Rejected -> error("Draft remap leads outside the draft: ${entry.rejection}")
        }
    }

    private fun definitionOf(
        palette: Palette,
        defaultIndex: PaletteIndex,
    ): PaletteDefinition =
        when (val definition = PaletteDefinition.create(palette, defaultIndex)) {
            is DomainValueResult.Created -> definition.value
            is DomainValueResult.Rejected -> error("Draft colour table is not a definition: ${definition.rejection}")
        }
}
