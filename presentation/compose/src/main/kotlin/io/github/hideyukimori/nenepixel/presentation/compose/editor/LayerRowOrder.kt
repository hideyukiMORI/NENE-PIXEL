package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** Bottom-first position and local-frame layer count belong to one row ordering concept. */
internal data class LayerRowOrder(
    val position: Int,
    val count: Int,
)
