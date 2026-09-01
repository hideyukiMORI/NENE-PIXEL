package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

public data class ViewportState private constructor(
    public val zoom: ViewportZoom,
    public val center: ViewportCenter,
) {
    public companion object {
        public fun initial(canvas: CanvasSize): ViewportState =
            ViewportState(
                zoom = ViewportZoom.initial(),
                center = ViewportCenter.midpoint(canvas),
            )

        public fun create(
            zoom: ViewportZoom,
            center: ViewportCenter,
        ): ViewportState = ViewportState(zoom, center)
    }
}
