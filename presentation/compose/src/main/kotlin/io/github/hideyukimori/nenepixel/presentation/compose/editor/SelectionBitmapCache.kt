package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.render.SelectionPreviewComposite
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer

/** Placement/orientation/mode drafts reuse one source preparation and the existing PreviewRaster's canvas buffers. */
internal class SelectionBitmapCache {
    private var source: DocumentState? = null
    private var sourceLayer: LayerId? = null
    private var sourceFrame: FrameId? = null
    private var sourceRegion: PixelRegion? = null
    private var composite: SelectionPreviewComposite? = null
    private var current: PixelTransfer? = null
    private var base: Bitmap? = null

    internal var preparationCount: Int = 0
        private set

    /** Keep same-source Identity preparation; a changed key drops it without preparing pixels. */
    fun hide(
        document: DocumentState,
        frameId: FrameId,
        layerId: LayerId,
        region: PixelRegion,
    ) {
        if (reusable(document, frameId, layerId, region)) current = null else forget()
    }

    fun forget() {
        source = null
        sourceLayer = null
        sourceFrame = null
        sourceRegion = null
        composite = null
        current = null
        base = null
    }

    fun paint(
        input: SelectionBitmapInput,
        raster: PreviewRaster,
    ): Boolean {
        if (!reusable(input.picture.document, input.picture.frameId, input.layerId, input.transfer.source)) {
            composite =
                SelectionPreviewComposite.prepare(
                    input.picture.document,
                    input.picture.frameId,
                    input.layerId,
                    input.transfer.source,
                )
            source = input.picture.document
            sourceFrame = input.picture.frameId
            sourceLayer = input.layerId
            sourceRegion = input.transfer.source
            preparationCount++
            current = null
        }
        val baseReplaced = base !== input.base
        if (baseReplaced) {
            base = input.base
            raster.replaceBase(input.committed::copyRenderedArgbInto)
            current = null
        }
        if (current !== input.transfer) {
            current = input.transfer
            raster.paintSelection(requireNotNull(composite).render(input.transfer))
        }
        return baseReplaced
    }

    private fun reusable(
        document: DocumentState,
        frameId: FrameId,
        layerId: LayerId,
        region: PixelRegion,
    ): Boolean = source === document && sourceFrame == frameId && sourceLayer == layerId && sourceRegion == region
}
