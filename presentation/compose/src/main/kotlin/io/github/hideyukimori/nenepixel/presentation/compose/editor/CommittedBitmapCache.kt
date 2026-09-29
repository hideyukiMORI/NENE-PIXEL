package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import io.github.hideyukimori.nenepixel.core.application.render.DocumentComposite
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeImage
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeRenderResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/**
 * One disposable rendering of the committed document shared by the canvas and the actual-size
 * window (ADR 0026). It is derived state keyed by every rendering input, never a second owner of
 * document semantics (QLT-016).
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
    private var sourceBackgroundArgb: Int? = null
    private var rendered: Bitmap? = null

    fun render(
        document: DocumentState,
        definition: PaletteDefinition,
        backgroundArgb: Int,
    ): Bitmap {
        if (source !== document || sourceDefinition !== definition || sourceBackgroundArgb != backgroundArgb) {
            source = document
            sourceDefinition = definition
            sourceBackgroundArgb = backgroundArgb
            rendered = composite(document, definition).toOpaqueRenderedBitmap(backgroundArgb)
        }
        return requireNotNull(rendered)
    }

    private fun composite(
        document: DocumentState,
        definition: PaletteDefinition,
    ): DocumentCompositeImage =
        when (val result = DocumentComposite.render(document, definition)) {
            is DocumentCompositeRenderResult.Rendered -> result.image
            DocumentCompositeRenderResult.IndexOutsidePalette -> error("Rendered index is outside the palette")
        }
}
