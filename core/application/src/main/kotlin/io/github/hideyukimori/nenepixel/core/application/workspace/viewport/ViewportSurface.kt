package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public data class ViewportSurface private constructor(
    public val widthPixels: Int,
    public val heightPixels: Int,
    public val pixelsPerDp: Double,
) {
    public companion object {
        public fun create(
            widthPixels: Int,
            heightPixels: Int,
            pixelsPerDp: Double,
        ): ViewportValueResult<ViewportSurface> =
            when {
                widthPixels <= 0 -> {
                    viewportRejected(ViewportValueRejection.NonPositiveSurfaceWidth(widthPixels))
                }

                heightPixels <= 0 -> {
                    viewportRejected(ViewportValueRejection.NonPositiveSurfaceHeight(heightPixels))
                }

                !pixelsPerDp.isFinite() -> {
                    viewportRejected(ViewportValueRejection.NonFinitePixelsPerDp(pixelsPerDp))
                }

                pixelsPerDp <= 0.0 -> {
                    viewportRejected(ViewportValueRejection.NonPositivePixelsPerDp(pixelsPerDp))
                }

                else -> {
                    viewportCreated(
                        ViewportSurface(
                            widthPixels,
                            heightPixels,
                            ViewportNumbers.canonicalizeZero(pixelsPerDp),
                        ),
                    )
                }
            }
    }
}
