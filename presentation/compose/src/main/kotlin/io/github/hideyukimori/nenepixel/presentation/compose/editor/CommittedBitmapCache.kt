package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import io.github.hideyukimori.nenepixel.core.application.render.DocumentComposite
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeImage
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeRenderResult
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftComposite
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/**
 * One disposable rendering of the committed document shared by the canvas and the actual-size
 * window (ADR 0026). It is derived state keyed by every rendering input, never a second owner of
 * document semantics (QLT-016). While a palette edit session is open the picture is the one its
 * draft would give once applied (ADR 0022); the document itself is unchanged.
 */
internal class CommittedBitmapCache {
    /** Nearest-neighbour pixel paint: exact integer multiples, no interpolation, at any scale. */
    val paint: Paint =
        Paint().apply {
            isAntiAlias = false
            isDither = false
            isFilterBitmap = false
        }

    private var source: DocumentState? = null
    private var sourceDefinition: PaletteDefinition? = null
    private var sourceSession: PaletteEditSession? = null
    private var rendered: Bitmap? = null
    private var renderedArgb: IntArray? = null

    /**
     * The committed picture with its alpha, or the draft's picture while [session] is open; rebuilt
     * only when the document, definition or session reference changes.
     */
    fun render(
        document: DocumentState,
        definition: PaletteDefinition,
        session: PaletteEditSession?,
    ): Bitmap {
        if (source !== document || sourceDefinition !== definition || sourceSession !== session) {
            source = document
            sourceDefinition = definition
            sourceSession = session
            val argb = composite(document, definition, session).toStraightArgb()
            renderedArgb = argb
            val size = document.size
            rendered = Bitmap.createBitmap(argb, size.width.value, size.height.value, Bitmap.Config.ARGB_8888)
        }
        return requireNotNull(rendered)
    }

    /** Copies the straight-alpha ARGB of the latest [render] into [target] (row-major); the array stays private. */
    fun copyRenderedArgbInto(target: IntArray) {
        val argb = requireNotNull(renderedArgb) { "Nothing has been rendered yet" }
        require(target.size == argb.size) { "Target holds ${target.size} pixels, the rendering ${argb.size}" }
        System.arraycopy(argb, 0, target, 0, argb.size)
    }

    private fun composite(
        document: DocumentState,
        definition: PaletteDefinition,
        session: PaletteEditSession?,
    ): DocumentCompositeImage =
        session?.let { draftComposite(document, it) } ?: committedComposite(document, definition)

    /** The draft's picture, or `null` when the document's palette is not the one the session started from. */
    private fun draftComposite(
        document: DocumentState,
        session: PaletteEditSession,
    ): DocumentCompositeImage? =
        when (val result = PaletteDraftComposite.render(document, session)) {
            is PaletteDraftCompositeResult.Rendered -> result.image
            PaletteDraftCompositeResult.SourceMismatch -> null
        }

    private fun committedComposite(
        document: DocumentState,
        definition: PaletteDefinition,
    ): DocumentCompositeImage =
        when (val result = DocumentComposite.render(document, definition)) {
            is DocumentCompositeRenderResult.Rendered -> result.image
            DocumentCompositeRenderResult.IndexOutsidePalette -> error("Rendered index is outside the palette")
        }
}
