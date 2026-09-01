package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public sealed interface ViewportValueRejection {
    public data class NonFiniteZoom internal constructor(
        public val attemptedValue: Double,
    ) : ViewportValueRejection

    public data class ZoomOutsideRange internal constructor(
        public val attemptedValue: Double,
    ) : ViewportValueRejection

    public data class NonFiniteCenter internal constructor(
        public val attemptedX: Double,
        public val attemptedY: Double,
    ) : ViewportValueRejection

    public data class NonPositiveSurfaceWidth internal constructor(
        public val attemptedValue: Int,
    ) : ViewportValueRejection

    public data class NonPositiveSurfaceHeight internal constructor(
        public val attemptedValue: Int,
    ) : ViewportValueRejection

    public data class NonFinitePixelsPerDp internal constructor(
        public val attemptedValue: Double,
    ) : ViewportValueRejection

    public data class NonPositivePixelsPerDp internal constructor(
        public val attemptedValue: Double,
    ) : ViewportValueRejection

    public data class NonFiniteSurfacePoint internal constructor(
        public val attemptedX: Double,
        public val attemptedY: Double,
    ) : ViewportValueRejection

    public data object UnsafeDerivedTransform : ViewportValueRejection

    public data object UnsafeDerivedGesture : ViewportValueRejection
}
