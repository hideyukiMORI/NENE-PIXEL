package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** The one inclusive rectangle/hit/whole-region clamp policy for raw selection pointers. */
internal object SelectionPointerPolicy {
    fun begin(
        session: SelectionSession,
        position: PixelPosition,
    ): SelectionSession {
        val previous = session.geometry.draft
        val inside = session.targetRegion?.contains(position) == true
        val next =
            if (inside && previous != null) {
                SelectionGeometry(previous, SelectionPointer.Translating(position, previous))
            } else {
                val source = rectangle(session.admission.document.size, position, position)
                SelectionGeometry(SelectionDraft(source, source.origin), SelectionPointer.Selecting(position, previous))
            }
        return session.withGeometry(next)
    }

    fun update(
        session: SelectionSession,
        pointer: SelectionPointer,
        point: DocumentPoint,
    ): SelectionSession {
        val canvas = session.admission.document.size
        val draft =
            when (pointer) {
                is SelectionPointer.Selecting -> {
                    val bounded =
                        position(
                            point.x.coerceIn(0, canvas.width.value - 1),
                            point.y.coerceIn(0, canvas.height.value - 1),
                        )
                    val source = rectangle(canvas, pointer.anchor, bounded)
                    SelectionDraft(source, source.origin)
                }

                is SelectionPointer.Translating -> {
                    translate(canvas, pointer, point)
                }
            }
        return if (draft == session.geometry.draft) session else session.withGeometry(SelectionGeometry(draft, pointer))
    }

    private fun translate(
        canvas: CanvasSize,
        pointer: SelectionPointer.Translating,
        point: DocumentPoint,
    ): SelectionDraft {
        val draft = pointer.previous
        val x =
            draft.destination.x.value
                .toLong() + point.x.toLong() -
                pointer.anchor.x.value
                    .toLong()
        val y =
            draft.destination.y.value
                .toLong() + point.y.toLong() -
                pointer.anchor.y.value
                    .toLong()
        val maximumX = (canvas.width.value - draft.source.size.width.value).toLong()
        val maximumY = (canvas.height.value - draft.source.size.height.value).toLong()
        val target = position(x.coerceIn(0L, maximumX).toInt(), y.coerceIn(0L, maximumY).toInt())
        return SelectionDraft(draft.source, target)
    }

    private fun rectangle(
        canvas: CanvasSize,
        first: PixelPosition,
        second: PixelPosition,
    ): PixelRegion {
        val left = minOf(first.x.value, second.x.value)
        val top = minOf(first.y.value, second.y.value)
        val width = maxOf(first.x.value, second.x.value) - left + 1
        val height = maxOf(first.y.value, second.y.value) - top + 1
        val size = CanvasSize.create(required(CanvasWidth.create(width)), required(CanvasHeight.create(height)))
        return required(PixelRegion.create(canvas, position(left, top), size))
    }

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(required(PixelX.create(x)), required(PixelY.create(y)))

    private fun <T> required(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Clamped selection geometry was rejected: ${result.rejection}")
        }
}
