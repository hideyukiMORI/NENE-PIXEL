package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Display-relative composition and whole-region edge anchoring; the domain owns orientation meaning. */
internal object SelectionTransformPolicy {
    fun fits(
        canvas: CanvasSize,
        draft: SelectionDraft,
        operation: PixelOrientation,
    ): Boolean {
        val size = draft.orientation.then(operation).orientedSize(draft.source.size)
        return size.width.value <= canvas.width.value && size.height.value <= canvas.height.value
    }

    /** Called after fits succeeds; clamping never resizes or clips a transformed rectangle. */
    fun transform(
        canvas: CanvasSize,
        draft: SelectionDraft,
        operation: PixelOrientation,
    ): SelectionDraft {
        val orientation = draft.orientation.then(operation)
        val size = orientation.orientedSize(draft.source.size)
        val x =
            draft.destination.x.value
                .coerceIn(0, canvas.width.value - size.width.value)
        val y =
            draft.destination.y.value
                .coerceIn(0, canvas.height.value - size.height.value)
        val destination = PixelPosition.create(required(PixelX.create(x)), required(PixelY.create(y)))
        return draft.copy(destination = destination, orientation = orientation)
    }

    private fun <T> required(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Clamped selection placement was rejected: ${result.rejection}")
        }
}
