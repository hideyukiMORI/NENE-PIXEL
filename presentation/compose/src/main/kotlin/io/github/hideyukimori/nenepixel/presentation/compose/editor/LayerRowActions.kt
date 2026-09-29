package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * What a layer-panel row can ask for (#144 U5): the layer route [callbacks], and [onRename], which opens the rename
 * dialog for a layer. The overlay remembers one instance per [callbacks]; U7 connects [onRename].
 */
internal class LayerRowActions(
    val callbacks: EditorLayerCallbacks,
    val onRename: (LayerId) -> Unit,
)
