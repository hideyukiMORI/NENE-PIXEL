package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PixelTransferComposite

/** Read-only affected-position projection. Mutable pixel storage never reaches presentation. */
public class SelectionPreviewImage internal constructor(
    private val composite: PixelTransferComposite,
    public val transfer: PixelTransfer,
) {
    public fun forEachPosition(consume: (PixelPosition) -> Unit) {
        composite.forEachPosition(transfer, consume)
    }

    public fun packedRgba8888At(position: PixelPosition): Int = composite.packedRgba8888At(position, transfer)
}
