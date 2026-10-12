package io.github.hideyukimori.nenepixel.core.domain.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

/** Destination position and exact orientation; canvas fit is admitted by [PixelTransfer]. */
public data class PixelPlacement private constructor(
    public val position: PixelPosition,
    public val orientation: PixelOrientation,
) {
    public companion object {
        public fun create(
            position: PixelPosition,
            orientation: PixelOrientation = PixelOrientation.Identity,
        ): PixelPlacement = PixelPlacement(position, orientation)
    }
}
