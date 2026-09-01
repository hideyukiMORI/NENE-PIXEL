package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public data class ViewportSurfacePoint private constructor(
    public val xPixels: Double,
    public val yPixels: Double,
) {
    public companion object {
        public fun create(
            xPixels: Double,
            yPixels: Double,
        ): ViewportValueResult<ViewportSurfacePoint> =
            if (ViewportNumbers.areFinite(xPixels, yPixels)) {
                viewportCreated(
                    ViewportSurfacePoint(
                        ViewportNumbers.canonicalizeZero(xPixels),
                        ViewportNumbers.canonicalizeZero(yPixels),
                    ),
                )
            } else {
                viewportRejected(ViewportValueRejection.NonFiniteSurfacePoint(xPixels, yPixels))
            }
    }
}
