package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/** One selected slot's colour intents; the runtime alone owns the draft and gesture history. */
internal class PaletteColorEditCallbacks(
    private val index: PaletteIndex,
    private val callbacks: EditorPaletteCallbacks,
) {
    fun begin(): Boolean = callbacks.colorEdit.begin(index).paletteNotice == null

    fun update(color: PixelColor) {
        callbacks.colorEdit.update(color)
    }

    fun finish() {
        callbacks.colorEdit.finish()
    }

    fun cancel() {
        callbacks.colorEdit.cancel()
    }

    fun commit(color: PixelColor) {
        callbacks.onEdit(PaletteDraftOperation.SetSlotColor(index, color))
    }

    fun step(color: PixelColor): Boolean {
        if (!begin()) return false
        update(color)
        finish()
        return true
    }
}
