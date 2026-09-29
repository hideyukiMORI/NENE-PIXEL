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
    private var rendered: Bitmap? = null
    private var renderedArgb: IntArray? = null

    /** The committed picture with its alpha; rebuilt only when the document or definition reference changes. */
    fun render(
        document: DocumentState,
        definition: PaletteDefinition,
    ): Bitmap {
        if (source !== document || sourceDefinition !== definition) {
            source = document
            sourceDefinition = definition
            val argb = composite(document, definition).toStraightArgb()
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
    ): DocumentCompositeImage =
        when (val result = DocumentComposite.render(document, definition)) {
            is DocumentCompositeRenderResult.Rendered -> result.image
            DocumentCompositeRenderResult.IndexOutsidePalette -> error("Rendered index is outside the palette")
        }
}
