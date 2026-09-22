package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

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

    private var source: PixelSnapshot? = null
    private var sourceDefinition: PaletteDefinition? = null
    private var sourceBackgroundArgb: Int? = null
    private var rendered: Bitmap? = null

    fun render(
        snapshot: PixelSnapshot,
        definition: PaletteDefinition,
        backgroundArgb: Int,
    ): Bitmap {
        if (source !== snapshot || sourceDefinition !== definition || sourceBackgroundArgb != backgroundArgb) {
            source = snapshot
            sourceDefinition = definition
            sourceBackgroundArgb = backgroundArgb
            rendered = snapshot.toOpaqueRenderedBitmap(definition, backgroundArgb)
        }
        return requireNotNull(rendered)
    }
}
