package io.github.hideyukimori.nenepixel.presentation.compose.input

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

internal sealed interface TouchTranslation {
    data class Mapped(
        val position: PixelPosition,
    ) : TouchTranslation

    data object Outside : TouchTranslation
}
