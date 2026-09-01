package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public class EditorCallbacks internal constructor(
    private val pointerDown: (PixelPosition) -> EditorRenderState,
    private val pointerMove: (PixelPosition) -> EditorRenderState,
    private val pointerEnd: (PixelPosition) -> EditorRenderState,
    private val pointerCancel: () -> EditorRenderState,
) {
    public fun onPointerDown(position: PixelPosition): EditorRenderState = pointerDown(position)

    public fun onPointerMove(position: PixelPosition): EditorRenderState = pointerMove(position)

    public fun onPointerEnd(position: PixelPosition): EditorRenderState = pointerEnd(position)

    public fun onPointerCancel(): EditorRenderState = pointerCancel()
}
