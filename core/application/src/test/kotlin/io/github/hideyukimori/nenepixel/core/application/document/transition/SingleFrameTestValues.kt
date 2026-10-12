package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals

/** Existing fixtures require exactly one frame; no frame fallback or flattening is permitted. */
internal fun DocumentState.singleFrameLayers(): List<Layer> = frames.single().layers

/** Pixel-only fixtures additionally require exactly one layer. */
internal fun DocumentState.singleFrameSnapshot(): PixelSnapshot = singleFrameLayers().single().snapshot

internal fun assertSingleFrameCell(
    document: DocumentState,
    position: PixelPosition,
    expected: PixelCell,
) {
    assertEquals(expected, cellAt(document.singleFrameSnapshot(), position))
}
