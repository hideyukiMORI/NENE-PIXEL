package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public sealed interface ViewportMappingResult<out T> {
    public data class Mapped<out T> internal constructor(
        public val value: T,
    ) : ViewportMappingResult<T>

    public data object OutsideSurface : ViewportMappingResult<Nothing>

    public data object OutsideCanvas : ViewportMappingResult<Nothing>
}

internal fun <T> viewportMapped(value: T): ViewportMappingResult<T> = ViewportMappingResult.Mapped(value)
