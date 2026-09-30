package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

/**
 * Straight-alpha ARGB the running gesture shows at one position (Issues #143, #147). It returns a primitive so
 * painting a gesture neither boxes a colour nor allocates per position.
 */
internal fun interface PreviewColorSource {
    fun argbAt(position: PixelPosition): Int
}
