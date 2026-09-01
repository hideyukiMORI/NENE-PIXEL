package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public data class ViewportGesture private constructor(
    public val previousFirst: ViewportSurfacePoint,
    public val previousSecond: ViewportSurfacePoint,
    public val currentFirst: ViewportSurfacePoint,
    public val currentSecond: ViewportSurfacePoint,
) {
    public companion object {
        public fun create(
            previousFirst: ViewportSurfacePoint,
            previousSecond: ViewportSurfacePoint,
            currentFirst: ViewportSurfacePoint,
            currentSecond: ViewportSurfacePoint,
        ): ViewportGesture =
            ViewportGesture(
                previousFirst,
                previousSecond,
                currentFirst,
                currentSecond,
            )
    }
}
