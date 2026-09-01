package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

public data class ViewportCenter private constructor(
    public val x: Double,
    public val y: Double,
) {
    public companion object {
        public fun create(
            x: Double,
            y: Double,
        ): ViewportValueResult<ViewportCenter> =
            if (ViewportNumbers.areFinite(x, y)) {
                viewportCreated(
                    ViewportCenter(
                        ViewportNumbers.canonicalizeZero(x),
                        ViewportNumbers.canonicalizeZero(y),
                    ),
                )
            } else {
                viewportRejected(ViewportValueRejection.NonFiniteCenter(x, y))
            }

        internal fun midpoint(canvas: CanvasSize): ViewportCenter =
            ViewportCenter(
                canvas.width.value.toDouble() / MIDPOINT_DIVISOR,
                canvas.height.value.toDouble() / MIDPOINT_DIVISOR,
            )

        private const val MIDPOINT_DIVISOR: Double = 2.0
    }
}
