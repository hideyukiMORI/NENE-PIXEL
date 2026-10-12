package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

/** Existing single-frame fixtures fail explicitly if given multiple frames; no first-frame fallback. */
internal object SingleFrameTestValues {
    fun frameOf(document: DocumentState): Frame = document.frames.single()

    fun layersOf(document: DocumentState): List<Layer> = frameOf(document).layers

    fun layerOf(document: DocumentState): Layer = layersOf(document).single()

    fun snapshotOf(document: DocumentState): PixelSnapshot = layerOf(document).snapshot
}
