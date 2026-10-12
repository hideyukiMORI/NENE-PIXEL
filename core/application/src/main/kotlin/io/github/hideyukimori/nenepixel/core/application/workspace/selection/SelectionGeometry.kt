package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion

/** Only geometry is retained; original indexed pixels remain in the admission's immutable document. */
internal data class SelectionGeometry(
    val draft: SelectionDraft?,
    val pointer: SelectionPointer?,
)

internal data class SelectionDraft(
    val source: PixelRegion,
    val destination: PixelPosition,
)

/** A pointer retains the starting draft so cancellation can restore it after an unfinished drag. */
internal sealed interface SelectionPointer {
    val previous: SelectionDraft?

    data class Selecting(
        val anchor: PixelPosition,
        override val previous: SelectionDraft?,
    ) : SelectionPointer

    data class Translating(
        val anchor: PixelPosition,
        override val previous: SelectionDraft,
    ) : SelectionPointer
}
