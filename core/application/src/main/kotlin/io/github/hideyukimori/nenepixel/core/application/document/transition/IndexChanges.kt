package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch

internal sealed interface IndexChanges {
    val canvas: CanvasSize
    val changeCount: Int

    data class NoIndexChanges(
        override val canvas: CanvasSize,
    ) : IndexChanges {
        override val changeCount: Int = 0
    }

    data class Changed(
        val patch: PixelPatch,
    ) : IndexChanges {
        override val canvas: CanvasSize
            get() = patch.canvas
        override val changeCount: Int
            get() = patch.changeCount
    }

    fun inverse(): IndexChanges =
        when (this) {
            is NoIndexChanges -> this
            is Changed -> Changed(patch.inverse())
        }
}
