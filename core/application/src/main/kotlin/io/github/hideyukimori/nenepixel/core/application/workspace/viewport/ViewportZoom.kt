package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public data class ViewportZoom private constructor(
    public val value: Double,
) {
    public companion object {
        public fun create(value: Double): ViewportValueResult<ViewportZoom> =
            when {
                !value.isFinite() -> {
                    viewportRejected(ViewportValueRejection.NonFiniteZoom(value))
                }

                value < ViewportNumbers.MIN_ZOOM || value > ViewportNumbers.MAX_ZOOM -> {
                    viewportRejected(ViewportValueRejection.ZoomOutsideRange(value))
                }

                else -> {
                    viewportCreated(ViewportZoom(ViewportNumbers.canonicalizeZero(value)))
                }
            }

        internal fun initial(): ViewportZoom = ViewportZoom(ViewportNumbers.MIN_ZOOM)

        internal fun bounded(value: Double): ViewportZoom =
            ViewportZoom(
                ViewportNumbers.canonicalizeZero(
                    value.coerceIn(ViewportNumbers.MIN_ZOOM, ViewportNumbers.MAX_ZOOM),
                ),
            )
    }
}
